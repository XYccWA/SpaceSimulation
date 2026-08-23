// TestFlow.java —— 完整验收测试流程自动化（系统属性门控）
//
// 用法：runClient 时加 -Dspacesim.testflow=true（如 JAVA_TOOL_OPTIONS 环境变量）。
// 启用后自动走完用户定义的验收链路：
//   打开游戏 -> 进入世界 -> 执行一条传送命令(tp @s 20050 0 0, x 在 [20010,20100], y=z=0)
//   -> 保存并退出到标题 -> 退出客户端
// 全程日志输出 [TestFlow] 状态，任何一步异常都会打 ERROR（但进程仍干净退出，
// 以便脚本用"退出码 0 + 日志含 VERIFIED"判定通过）。
package org.xyccwa.space_simulation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xyccwa.space_simulation.SpaceSimulation;

@EventBusSubscriber(value = Dist.CLIENT, modid = SpaceSimulation.MOD_ID)
public class TestFlow {

    private static final Logger LOGGER = LoggerFactory.getLogger("TestFlow");
    /** 门控系统属性：-Dspacesim.testflow=true */
    private static final String GATE = "spacesim.testflow";

    /** 传送目标：x 在用户要求的 [20010,20100] 内取中值，y=z=0。 */
    private static final double TARGET_X = 20050.0;

    private static boolean enabled = false;
    private static int state = 0;
    private static int verifyTicks = 0;
    private static int exitTicks = 0;
    private static boolean quitScreenInstalled = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled) {
            enabled = Boolean.getBoolean(GATE);
            if (!enabled) {
                return;
            }
            LOGGER.info("[TestFlow] enabled by -D{} = true", GATE);
        }

        Minecraft mc = Minecraft.getInstance();
        switch (state) {
            case 0 -> { // 等世界就绪（进入世界）
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null
                        && mc.getSingleplayerServer().isReady()) {
                    LOGGER.info("[TestFlow] step1 enter-world OK, player=({}, {}, {})",
                            round(mc.player.getX()), round(mc.player.getY()), round(mc.player.getZ()));
                    state = 1;
                }
            }
            case 1 -> { // 执行一条传送命令
                mc.player.connection.sendCommand("tp @s " + TARGET_X + " 0 0");
                LOGGER.info("[TestFlow] step2 sent teleport command: tp @s {} 0 0", TARGET_X);
                verifyTicks = 0;
                state = 2;
            }
            case 2 -> { // 验证传送结果（x 在 [20010,20100]，y/z 接近 0）
                verifyTicks++;
                double x = mc.player.getX();
                double y = mc.player.getY();
                double z = mc.player.getZ();
                boolean ok = x >= 20010.0 && x <= 20100.0 && Math.abs(y) <= 10.0 && Math.abs(z) <= 10.0;
                if (ok) {
                    LOGGER.info("[TestFlow] step3 VERIFIED player at ({}, {}, {})", round(x), round(y), round(z));
                    state = 3;
                } else if (verifyTicks >= 200) {
                    LOGGER.error("[TestFlow] step3 VERIFY TIMEOUT (position never matched), last=({}, {}, {})",
                            round(x), round(y), round(z));
                    state = 3; // 位置不对也继续走保存退出，避免进程挂死；判定看日志
                }
            }
            case 3 -> { // 保存并退出到标题：装一个专用 Screen，在渲染期执行 vanilla 按钮同款流程
                if (!quitScreenInstalled) {
                    quitScreenInstalled = true;
                    LOGGER.info("[TestFlow] step4 installing SaveQuitScreen");
                    mc.setScreen(new SaveQuitScreen());
                }
            }
            case 4 -> { // 等标题屏 -> 退出客户端
                if (mc.screen instanceof TitleScreen) {
                    LOGGER.info("[TestFlow] step5 at title screen, calling mc.stop()");
                    mc.stop();
                    state = 5;
                } else if (++exitTicks > 600) {
                    LOGGER.error("[TestFlow] step5 TIMEOUT waiting for title screen");
                    mc.stop();
                    state = 5;
                }
            }
            default -> { /* state == 5：流程结束，仅等 JVM 退出 */ }
        }
    }

    /**
     * 在渲染期执行原版"保存并退出到标题"三连（PauseScreen.onDisconnect 同款）：
     * level.disconnect() -> mc.disconnect(保存中...) -> 回标题。
     * mc.disconnect 内部会自动等待整合服务器停止；服务器线程收到断开后走
     * halt(false) -> stopServer()（保存玩家+区块+解锁世界文件），全链路自包含。
     * 放在 Screen.render 里而非 tick 处理器，与 vanilla 按钮点击同一上下文，
     * 避免从 ClientTickEvent(在 Minecraft.tick 内) 重入 runTick 的嵌套渲染。
     */
    public static class SaveQuitScreen extends Screen {
        private boolean ran = false;

        public SaveQuitScreen() {
            super(Component.translatable("menu.savingLevel"));
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (!ran) {
                ran = true;
                Minecraft mc = Minecraft.getInstance();
                LOGGER.info("[TestFlow] step4 executing save-and-quit");
                try {
                    if (mc.level != null) {
                        mc.level.disconnect();
                    }
                    mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
                    mc.setScreen(new TitleScreen());
                    LOGGER.info("[TestFlow] step4 save-and-quit done, back at title");
                } catch (Throwable t) {
                    LOGGER.error("[TestFlow] step4 save-and-quit FAILED", t);
                }
                TestFlow.state = 4; // 无论成败都进入"等标题屏->退出客户端"，保证进程能干净退出
            }
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
