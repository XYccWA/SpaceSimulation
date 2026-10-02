package org.xyccwa.space_simulation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;
import org.xyccwa.space_simulation.asteroid.AsteroidProximityService;
import org.xyccwa.space_simulation.asteroid.data.AsteroidDataLoader;
import org.xyccwa.space_simulation.attachment.SpaceSimulationAttachments;
import org.xyccwa.space_simulation.client.WorldSphereRenderer;
import org.xyccwa.space_simulation.command.AsteroidCommand;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;
import org.xyccwa.space_simulation.damage.PlayerAccelerationDamage;
import org.xyccwa.space_simulation.damage.SolarHeatDamage;
import org.xyccwa.space_simulation.lighting.LightingSettings;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;
import org.xyccwa.space_simulation.modItem.SpaceSimulationCreativeTab;
import org.xyccwa.space_simulation.modItem.SpaceSimulationItem;
import org.xyccwa.space_simulation.network.ModPayloads;

@Mod(SpaceSimulation.MOD_ID)
public class SpaceSimulation {
    public static final String MOD_ID = "space_simulation";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpaceSimulation(IEventBus modEventBus) {
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        //配置
        container.registerConfig(ModConfig.Type.STARTUP, SpaceSimulationConfig.SPEC);
        // 轨道引力参数：注入配置读取器（惰性解析，见 refreshGravity）
        refreshGravity();
        //光照开关缓存：mixin 热路径不能每次都读配置，只在加载/重载时刷新
        modEventBus.addListener((ModConfigEvent.Loading event) -> {
            LightingSettings.refresh();
            refreshGravity();
        });
        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            LightingSettings.refresh();
            refreshGravity();
        });
        //方块
        SpaceSimulationBlock.BLOCKS.register(modEventBus);
        //物品
        SpaceSimulationItem.ITEMS.register(modEventBus);
        //创造模式物品栏
        SpaceSimulationCreativeTab.CREATIVE_MODE_TABS.register(modEventBus);
        //创建伤害处理器
        PlayerAccelerationDamage damageHandler = new PlayerAccelerationDamage();
        //太阳高温伤害与表面处死
        SolarHeatDamage solarHeatDamage = new SolarHeatDamage();
        //注册数据
        SpaceSimulationAttachments.register(modEventBus);
        //注册网络包
        modEventBus.addListener(ModPayloads::register);


        NeoForge.EVENT_BUS.register(damageHandler);
        NeoForge.EVENT_BUS.register(solarHeatDamage);

        // 程序化小行星系统：/asteroid 命令（meta / belts / info <index> / near / loader / reload）
        NeoForge.EVENT_BUS.addListener(AsteroidCommand::register);
        // 玩家轨道力学：/orbit 命令（info / mu / circular）
        NeoForge.EVENT_BUS.addListener(org.xyccwa.space_simulation.command.OrbitCommand::register);
        // 小行星数据包（asteroid_belt / asteroid_type / structure）：随数据包重载原子安装新宇宙
        NeoForge.EVENT_BUS.addListener(SpaceSimulation::onAddReloadListeners);
        // 兜底：服务器启动完成后立即解析安装一次（即使 reload 监听因故未触发，进世界前也已生效）
        NeoForge.EVENT_BUS.addListener(SpaceSimulation::onServerStarted);
        // 小行星近邻加载服务：服务端每 tick 自动驱动（预载索引分帧 + 强载每 tick 检索）
        NeoForge.EVENT_BUS.addListener(AsteroidProximityService::tick);
        // 小行星实体化：自动窗口（强载半径内自动实体化 / 离开自动卸载）+ 每 tick 位姿驱动
        NeoForge.EVENT_BUS.addListener(org.xyccwa.space_simulation.asteroid.entity.AsteroidEntityifyService::tick);
        // 停服前清空活动小行星子层级，避免它们被存档成跨会话残留
        NeoForge.EVENT_BUS.addListener(
                org.xyccwa.space_simulation.asteroid.entity.AsteroidEntityifyService::onServerStopping);
        // 低频性能统计（默认每 60 秒一行）：tick 计算耗时 avg/max、实体/区块/小行星数、堆内存
        NeoForge.EVENT_BUS.addListener(org.xyccwa.space_simulation.diagnostics.PerfLogger::onTickPre);
        NeoForge.EVENT_BUS.addListener(org.xyccwa.space_simulation.diagnostics.PerfLogger::onTickPost);

        LOGGER.info("[小行星数据包] 事件监听已注册（AddReloadListenerEvent / ServerStartedEvent）");

        // 客户端:注册世界球体着色器(mod 总线事件,服务器端不加载客户端类)
        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(WorldSphereRenderer::registerShaders);
            // 内建光影包必须尽早落盘：Iris 会在游戏启动早期扫描 shaderpacks 目录，
            // 晚于它写入会导致本次启动仍编译到磁盘上的旧文件（修复要等下一次启动才生效）。
            org.xyccwa.space_simulation.client.ShaderPackInstaller.deployEarly();
        }
    }

    /**
     * 轨道力学参数缓存：引力参数 μ 在每 tick 的积分热路径上被读取，不能每次都查配置。
     * 采用"惰性解析 + 配置重载刷新"：STARTUP 配置在构造期已加载，但 ModConfigEvent.Loading
     * 实测在本监听器注册之前就触发了，所以这里注入读取器，第一次用到时才解析。
     * 客户端收到服务端 OrbitalParamsPayload 后会用 setMu 覆盖（并且不再被本地配置改回）。
     */
    private static void refreshGravity() {
        org.xyccwa.space_simulation.orbital.Gravity.setFallback(() -> {
            boolean enabled = SpaceSimulationConfig.orbitalMechanicsEnabled.get();
            return enabled ? SpaceSimulationConfig.orbitalMu.get() : 0.0;
        });
        try {
            LOGGER.info("[轨道] 引力参数读取器已注入（μ = {}）",
                    org.xyccwa.space_simulation.orbital.Gravity.mu());
        } catch (Throwable t) {
            // 配置尚未就绪：保持惰性，第一次真正用到时再解析
            LOGGER.info("[轨道] 引力参数读取器已注入（配置尚未就绪，稍后惰性解析）");
        }
    }

    /**
     * 小行星数据包：数据包重载（/reload、进世界时的首次加载）时注册解析监听器。
     */
    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        LOGGER.info("[小行星数据包] AddReloadListenerEvent 触发，注册数据包解析监听器");
        event.addListener(new AsteroidDataLoader());
    }

    /**
     * 小行星数据包兜底：服务器启动完成后立即解析安装一次（不依赖 reload 时序）。
     */
    private static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        LOGGER.info("[小行星数据包] ServerStartedEvent 触发，开始解析数据包");
        try {
            AsteroidDataLoader.loadNow(event.getServer().getResourceManager());
        } catch (Throwable t) {
            LOGGER.error("[小行星数据包] 服务器启动解析失败", t);
        }
    }

    /**
     * 探测 rapier 原生桥是否在运行时类路径（资源探测，不加载类：
     * 提前加载 Rapier3D 会让 mixin 报告 "loaded too early" 而跳过重基包装）。
     */
    private static boolean isRapierPresent() {
        try {
            return SpaceSimulation.class.getClassLoader()
                    .getResource("dev/ryanhcode/sable/physics/impl/rapier/Rapier3D.class") != null;
        } catch (Throwable t) {
            return false;
        }
    }

}
