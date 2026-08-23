package org.xyccwa.space_simulation.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.api.EntityRotation;

/**
 * 太阳渲染冒烟测试(系统属性门控:-Dspacesim.spheretest=true)。
 *
 * 链路:进入世界 -> 传送到球外 150000 格看向原点(太阳入视野,渲染路径执行)
 *      -> 等待稳定 -> 截 1 张图(可选查看) -> 直接退出客户端。
 * 仅验证"进入世界并退出且无异常";失败/超时也会自动干净退出。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SpaceSimulation.MOD_ID)
public class SphereVisualTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("SphereTest");
    /** 门控系统属性:-Dspacesim.spheretest=true */
    private static final String GATE = "spacesim.spheretest";
    /** 观察位置:球外 450000 格,回望原点(太阳半径 150000,张角半角 ≈ 19.5°)。 */
    private static final double VIEW_X = 450000.0;

    private static boolean enabled = false;
    private static int state = 0;
    private static int waitTicks = 0;
    private static boolean screenshotRequested = false;
    private static boolean screenshotDone = false;
    /** 全局总超时:tick 数,超过后强制退出客户端(防止挂死)。 */
    private static final int GLOBAL_TIMEOUT_TICKS = 7200; // 6 分钟
    private static int totalTicks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled) {
            enabled = Boolean.getBoolean(GATE);
            if (!enabled) {
                return;
            }
            LOGGER.info("[SphereTest] enabled by -D{} = true", GATE);
        }

        Minecraft mc = Minecraft.getInstance();
        // 全局总超时兜底:任何卡死状态都会强制干净退出
        totalTicks++;
        if (totalTicks > GLOBAL_TIMEOUT_TICKS && state != 9) {
            LOGGER.error("[SphereTest] GLOBAL TIMEOUT ({} ticks), forcing clean exit", GLOBAL_TIMEOUT_TICKS);
            mc.stop();
            state = 9;
            return;
        }
        switch (state) {
            case 0 -> { // 等世界就绪与渲染器初始化(半径已按种子确定),传送到球外 3R 看向原点
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null
                        && mc.getSingleplayerServer().isReady()) {
                    waitTicks++;
                    if (waitTicks > 2400) {
                        LOGGER.error("[SphereTest] TIMEOUT waiting for radius init");
                        mc.stop();
                        state = 9;
                    } else if (waitTicks > 5 && WorldSphereRenderer.isRadiusInitialized()) {
                        double radius = WorldSphereRenderer.getCurrentRadius();
                        double dist = radius * 3.0;
                        LOGGER.info("[SphereTest] step1 world ready, radius={} tp to ({},0,0)", radius, dist);
                        mc.player.connection.sendCommand("tp @s " + dist + " 0 0 90 0");
                        waitTicks = 0;
                        state = 1;
                    }
                } else if (++waitTicks > 2400) {
                    // 120 秒没进世界(如 quickPlay 失败),直接退出,不挂死
                    LOGGER.error("[SphereTest] TIMEOUT waiting for world (quickplay likely failed)");
                    mc.stop();
                    state = 9;
                }
            }
            case 1 -> { // 重置四元数朝向(看向 -X = 球心),等传送与渲染稳定
                if (waitTicks == 1 && mc.player instanceof EntityRotation rot) {
                    rot.setOrientation(new org.joml.Quaternionf().rotationY((float) -Math.PI / 2.0F));
                    mc.player.setYRot(90.0F);
                    mc.player.setXRot(0.0F);
                    LOGGER.info("[SphereTest] orientation reset, facing -X (origin)");
                }
                if (waitTicks == 10) {
                    LOGGER.info("[SphereTest] player pos=({}, {}, {}) radius={}",
                            mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            WorldSphereRenderer.getCurrentRadius());
                }
                if (++waitTicks > 100) { // 5 秒:太阳渲染路径稳定运行无异常
                    LOGGER.info("[SphereTest] step2 request screenshot");
                    screenshotRequested = true;
                    screenshotDone = false;
                    waitTicks = 0;
                    state = 2;
                }
            }
            case 2 -> { // 截图 A(正对太阳)完成,转向背对(+X)验证背后无空洞
                if (screenshotDone) {
                    LOGGER.info("[SphereTest] step3 turning away from sun (+X)");
                    if (mc.player instanceof EntityRotation rot) {
                        rot.setOrientation(new org.joml.Quaternionf().rotationY((float) Math.PI / 2.0F));
                        mc.player.setYRot(-90.0F);
                    }
                    waitTicks = 0;
                    state = 3;
                } else if (++waitTicks > 200) {
                    LOGGER.error("[SphereTest] TIMEOUT waiting screenshot A");
                    mc.stop();
                    state = 9;
                }
            }
            case 3 -> { // 等朝向稳定,截图 B(背对太阳)
                if (++waitTicks > 40) {
                    LOGGER.info("[SphereTest] step4 request back-facing screenshot");
                    screenshotRequested = true;
                    screenshotDone = false;
                    waitTicks = 0;
                    state = 4;
                }
            }
            case 4 -> { // 截图完成(或超时)后直接退出
                if (screenshotDone || ++waitTicks > 200) {
                    LOGGER.info("[SphereTest] step5 done, stopping client");
                    mc.stop(); // 直接退出,异步保存,不滞留主界面
                    state = 9;
                }
            }
            default -> { /* state == 9:流程结束,等 JVM 退出 */ }
        }
    }

    /** 由 WorldSphereRenderer 在 AFTER_LEVEL(球体渲染完成后)调用,同帧截图。 */
    public static void onAfterLevelRender(Minecraft mc) {
        if (screenshotRequested && !screenshotDone) {
            screenshotRequested = false;
            boolean ok = WorldSphereRenderer.captureScreenshot(mc);
            LOGGER.info("[SphereTest] screenshot captured ok={}", ok);
            screenshotDone = true;
        }
    }
}
