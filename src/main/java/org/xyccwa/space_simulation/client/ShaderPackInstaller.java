package org.xyccwa.space_simulation.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

/**
 * 内建光影包（Iris shader pack）的运行时分发与启用。
 *
 * <p>Iris 只从 {@code <gameDir>/shaderpacks/} 读取光影包，因此打进模组 jar 的资源必须
 * 在客户端启动时释放到该目录。包内容由 {@link #FILES} 显式列出（Iris 光影包无法从 jar
 * 内直接加载，只能落盘），并用一个版本标记文件避免每次启动重复写入、也避免覆盖用户
 * 对包内容的本地修改。
 *
 * <p>Iris 是软依赖：类路径上不存在 Iris 时这里整体跳过，模组照常运行。所有对 Iris 类的
 * 引用都隔离在 {@link IrisBridge} 内部类中，只有确认 Iris 存在后才会被加载。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SpaceSimulation.MOD_ID)
public class ShaderPackInstaller {

    /** Iris 主类的资源路径（存在性探测，不加载类）。 */
    private static final String IRIS_MARKER = "net/irisshaders/iris/Iris.class";

    /** 光影包目录名（即 {@code shaderpacks/} 下的目录名，也是 Iris 里的包名）。 */
    private static final String PACK_NAME = RadialSunDirection.SHADER_PACK_NAME;

    /** 打包在 jar 内的资源根。 */
    private static final String RESOURCE_ROOT = "/shaderpacks/" + PACK_NAME + "/";

    /** 版本标记文件名（写入包目录）。包内容变更时递增 {@link #PACK_VERSION} 即可强制重新释放。 */
    private static final String VERSION_MARKER = ".spacesim_pack_version";

    /** 内建光影包内容版本；改动 shader 资源后必须递增，否则老玩家不会更新。 */
    private static final String PACK_VERSION = "16";

    /** 光影包全部文件（相对 {@code shaders/} 目录）。 */
    private static final List<String> FILES = List.of(
            "shaders.properties",
            "lib/space_settings.glsl",
            "lib/space_light.glsl",
            "shadow.vsh",
            "shadow.fsh",
            "gbuffers_terrain.vsh",
            "gbuffers_terrain.fsh",
            "gbuffers_entities.vsh",
            "gbuffers_entities.fsh",
            "gbuffers_block.vsh",
            "gbuffers_block.fsh",
            "gbuffers_water.vsh",
            "gbuffers_water.fsh",
            "gbuffers_hand.vsh",
            "gbuffers_hand.fsh",
            "gbuffers_textured.vsh",
            "gbuffers_textured.fsh",
            "gbuffers_textured_lit.vsh",
            "gbuffers_textured_lit.fsh",
            "gbuffers_damagedblock.vsh",
            "gbuffers_damagedblock.fsh",
            "gbuffers_basic.vsh",
            "gbuffers_basic.fsh",
            "gbuffers_skybasic.vsh",
            "gbuffers_skybasic.fsh",
            "gbuffers_skytextured.vsh",
            "gbuffers_skytextured.fsh");

    private static boolean enableHandled = false;

    /**
     * 在 mod 构造阶段（游戏启动最早期）同步把内建光影包释放到磁盘。
     *
     * <p><b>为什么必须这么早</b>：Iris 在游戏启动早期就会扫描 {@code shaderpacks/} 目录并编译
     * 光影包。若像最初那样放在 {@code FMLClientSetupEvent} 的 {@code enqueueWork} 里，
     * 部署会晚于 Iris 的扫描，导致<b>本次启动仍然编译到上一次遗留在磁盘上的旧文件</b>——
     * 表现为修复"要等下一次启动才生效"，甚至看起来完全没生效（错误信息与行号一字不差）。
     *
     * <p>这里不读配置（构造阶段配置尚未加载），无条件释放；{@code builtInShaderPack}
     * 配置项仍由 {@link #onClientSetup} 的后备路径遵守。
     */
    public static void deployEarly() {
        if (!isIrisPresent()) {
            return;
        }
        installFiles();
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!isIrisPresent()) {
            SpaceSimulation.LOGGER.info("[ShaderPack] Iris not found on the classpath; built-in shader pack unavailable");
            return;
        }
        if (!SpaceSimulationConfig.builtInShaderPack.get()) {
            return;
        }
        // 文件 IO 不阻塞启动流程
        event.enqueueWork(ShaderPackInstaller::installFiles);
    }

    /**
     * 首次客户端 tick 时尝试自动启用内建光影包。
     *
     * <p>等到 tick 才做，是为了确保 Iris 自身的配置已经初始化完成（{@code Iris.onLoadingComplete()}
     * 早于首帧但晚于 mod 构造阶段）。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (enableHandled || !isIrisPresent()) {
            return;
        }
        enableHandled = true;
        if (!SpaceSimulationConfig.builtInShaderPack.get() || !SpaceSimulationConfig.autoEnableShaderPack.get()) {
            return;
        }
        try {
            IrisBridge.enableIfUnset();
        } catch (Throwable t) {
            SpaceSimulation.LOGGER.warn("[ShaderPack] auto-enable failed, please select '{}' manually in the Iris shader menu",
                    PACK_NAME, t);
        }
    }

    private static boolean isIrisPresent() {
        try {
            return ShaderPackInstaller.class.getClassLoader().getResource(IRIS_MARKER) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 把内建光影包释放到 {@code <gameDir>/shaderpacks/<PACK_NAME>/}。 */
    private static void installFiles() {
        try {
            Path root = FMLPaths.GAMEDIR.get().resolve("shaderpacks").resolve(PACK_NAME);
            Path marker = root.resolve(VERSION_MARKER);

            if (Files.isRegularFile(marker)) {
                String existing = Files.readString(marker, StandardCharsets.UTF_8).trim();
                if (PACK_VERSION.equals(existing)) {
                    return; // 已是最新版，不重复写入（保留用户的本地修改）
                }
            }

            int written = 0;
            for (String rel : FILES) {
                Path target = root.resolve("shaders").resolve(rel);
                Files.createDirectories(target.getParent());
                try (InputStream in = ShaderPackInstaller.class.getResourceAsStream(RESOURCE_ROOT + "shaders/" + rel)) {
                    if (in == null) {
                        SpaceSimulation.LOGGER.warn("[ShaderPack] built-in resource missing: {}", rel);
                        continue;
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    written++;
                }
            }
            Files.writeString(marker, PACK_VERSION, StandardCharsets.UTF_8);
            SpaceSimulation.LOGGER.info("[ShaderPack] deployed built-in shader pack ({} files, v{}) to {}",
                    written, PACK_VERSION, root);
        } catch (Exception e) {
            SpaceSimulation.LOGGER.error("[ShaderPack] failed to deploy built-in shader pack", e);
        }
    }

    /**
     * Iris 类引用的隔离层：只有确认 Iris 存在后才会被加载。
     */
    private static final class IrisBridge {

        private IrisBridge() {
        }

        /**
         * 若用户当前未选择任何光影包，则选中内建包并启用；已有其它选择时绝不覆盖。
         */
        static void enableIfUnset() throws IOException {
            net.irisshaders.iris.config.IrisConfig config = net.irisshaders.iris.Iris.getIrisConfig();
            String current = config.getShaderPackName().orElse(null);

            boolean unset = current == null
                    || current.isEmpty()
                    || current.equalsIgnoreCase("off")
                    || current.equalsIgnoreCase("disabled");
            boolean alreadyOurs = PACK_NAME.equals(current);

            if (!unset && !alreadyOurs) {
                SpaceSimulation.LOGGER.info("[ShaderPack] another shader pack is selected ({}); leaving it untouched",
                        current);
                return;
            }
            if (alreadyOurs && config.areShadersEnabled()) {
                return;
            }

            config.setShaderPackName(PACK_NAME);
            config.setShadersEnabled(true);
            config.save();
            net.irisshaders.iris.Iris.reload();
            SpaceSimulation.LOGGER.info("[ShaderPack] enabled built-in shader pack '{}'", PACK_NAME);
        }
    }
}
