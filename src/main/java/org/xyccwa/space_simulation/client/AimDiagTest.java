// AimDiagTest.java —— 准星/拾取对齐诊断自动化（-Dspacesim.aimdiagtest=true）
//
// 流程：进入世界 -> 确保前方近处有方块（在玩家 +Z / 朝向前 2 格处放一块
// 玻璃，保证 pick 有命中对象；用命令放但干净起见放一块可回放的临时方块）
// -> 依次设 4 个姿态，每个停留若干 tick 让 AimDiagnostics 每 20 tick 打一条
// [AimDiag] 日志 -> 全部采完自动退出客户端。
// 姿态序列（四元数 + 欧拉角回写，与玩家实际操作一致）：
//   0 直立   q=identity
//   1 俯仰+45
//   2 俯仰-45
//   3 滚转+90（侧身）
// 每个姿态停留 45 tick（约 2 秒，>20 tick 采样窗口，保证至少 2 条 [AimDiag] 日志）。
// 诊断数据在 run/logs/latest.log 按 [AimDiag] 过滤即可，不依赖读者在游戏内操作。
package org.xyccwa.space_simulation.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.api.EntityRotation;

@EventBusSubscriber(value = Dist.CLIENT, modid = SpaceSimulation.MOD_ID)
public class AimDiagTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("AimDiagTest");
    private static final String GATE = "spacesim.aimdiagtest";

    private static boolean enabled = false;
    private static int state = 0;
    private static int ticks = 0;
    /** 每个姿态停留的 tick 数（>20 保证 AimDiag 采样窗口内至少 2 条）。 */
    private static final int HOLD = 46;
    /** 全局总超时 tick，防止挂死。 */
    private static final int GLOBAL_TIMEOUT = 3600;
    private static int totalTicks = 0;

    /** 姿态列表：名称 + 四元数（rotationX=绕 X 轴俯仰，rotationZ=绕 Z 轴滚转，右乘叠加在直立基准上）。 */
    private static final String[] NAMES = { "up", "pitch+45", "pitch-45", "roll+90" };

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled) {
            enabled = Boolean.getBoolean(GATE);
            if (!enabled) {
                return;
            }
            LOGGER.info("[AimDiagTest] enabled by -D{} = true", GATE);
        }
        Minecraft mc = Minecraft.getInstance();
        totalTicks++;
        if (totalTicks > GLOBAL_TIMEOUT && state != 99) {
            LOGGER.error("[AimDiagTest] GLOBAL TIMEOUT, forcing exit");
            mc.stop();
            state = 99;
            return;
        }
        if (mc.level == null || mc.player == null || !(mc.player instanceof EntityRotation rot) || !rot.hasOrientation()) {
            return;
        }
        switch (state) {
            case 0 -> { // 等世界就绪
                if (mc.getSingleplayerServer() != null && mc.getSingleplayerServer().isReady()) {
                    LOGGER.info("[AimDiagTest] step1 world ready, starting poses");
                    ticks = 0;
                    state = 2;
                }
            }
            case 2 -> { // 每姿态 HOLD tick 后换下一个
                if (ticks % HOLD == 0) {
                    int idx = (ticks / HOLD);
                    if (idx >= NAMES.length) {
                        LOGGER.info("[AimDiagTest] step3 all poses done, stopping client");
                        mc.stop();
                        state = 99;
                        return;
                    }
                    Quaternionf q = pose(idx);
                    rot.setOrientation(q);
                    mc.player.setYRot((float) Math.toDegrees(Math.atan2(-forward(q).x, forward(q).z)));
                    mc.player.setXRot((float) Math.toDegrees(Math.asin(-forward(q).y)));
                    LOGGER.info("[AimDiagTest] set pose[{}]={} q=({},{},{},{})",
                            idx, NAMES[idx], q.x, q.y, q.z, q.w);
                }
                ticks++;
            }
            default -> { /* state==99: 结束 */ }
        }
    }

    /** 生成第 idx 个姿态四元数（在直立基准上叠加）。 */
    private static Quaternionf pose(int idx) {
        Quaternionf q = new Quaternionf();
        switch (idx) {
            case 1 -> q.mul(new Quaternionf().rotationX((float) Math.toRadians(45.0F)));
            case 2 -> q.mul(new Quaternionf().rotationX((float) Math.toRadians(-45.0F)));
            case 3 -> q.mul(new Quaternionf().rotationZ((float) Math.toRadians(90.0F)));
            default -> { }
        }
        return q.normalize();
    }

    private static org.joml.Vector3f forward(Quaternionf q) {
        return new org.joml.Vector3f(0.0F, 0.0F, 1.0F).rotate(q);
    }
}
