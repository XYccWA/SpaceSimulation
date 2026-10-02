package org.xyccwa.space_simulation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.xyccwa.space_simulation.api.EntityRotation;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;
import org.xyccwa.space_simulation.network.PlayerOrbitStatePayload;
import org.xyccwa.space_simulation.orbital.Gravity;
import org.xyccwa.space_simulation.orbital.OrbitalBody;
import org.xyccwa.space_simulation.orbital.OrbitalPhysics;
import org.xyccwa.space_simulation.util.FlightPhysics;

/**
 * 客户端本地预测 + 服务器权威对账。
 *
 * 本地每 tick 跑与服务器完全相同的积分（输入延迟为零、画面顺滑），并把
 * (序号 seq, 位置, 速度, 推力) 记进环形历史。服务器每 tick 下发它的权威状态并带上
 * "该状态对应哪个 seq"：
 *   - 在历史里找到同 seq 的预测值，误差 ≤ 阈值（配置 orbitalCorrectionBlocks）→ 什么都不做；
 *   - 误差超阈值 → 回滚到服务器状态，再用历史里记录的推力逐 tick 重放到当前 seq 并归位。
 *
 * 这样服务器始终是权威（作弊/丢包/碰撞分歧都会被拉回），本地又不必承受一个 tick 的输入延迟。
 */
public final class PlayerOrbitClient {

    /** 环形历史长度（tick）。64 tick 足以覆盖任何正常往返延迟。 */
    private static final int HISTORY = 64;

    private static final long[] SEQ = new long[HISTORY];
    private static final double[][] HIST_POS = new double[HISTORY][];
    private static final double[][] HIST_VEL = new double[HISTORY][];
    private static final double[][] HIST_THRUST = new double[HISTORY][];

    /** 客户端本地物理 tick 计数（每跑一次预测 +1），随控制包发给服务器。 */
    private static int tickSeq = 0;
    /** 最近一次预测使用的 seq（供 ClientTickHandler 发包时携带）。 */
    private static int lastSeq = 0;

    private static long corrections = 0;
    private static double lastCorrectionError = 0.0;
    /** 上一次对账匹配到的序号偏移（诊断用：服务器与客户端之间的固定流水线偏移）。 */
    private static int lastMatchOffset = 0;
    /** 连续多少次权威状态在本地历史里找不到对应项（超过阈值就强制对齐）。 */
    private static int unmatchedStreak = 0;
    /** 本地权威速度（块/tick）：不依赖实体 deltaMovement，原版 aiStep 会把小分量清零。 */
    private static final double[] LOCAL_VEL = new double[3];
    private static boolean localVelValid = false;

    static {
        // 自注册：只要本类被加载（客户端），通用侧的钩子就指向本地预测
        org.xyccwa.space_simulation.orbital.OrbitalSideHooks.setClientTicker(PlayerOrbitClient::tick);
    }

    private PlayerOrbitClient() {
    }

    /** 断线/切世界时清空。 */
    public static void clear() {
        for (int i = 0; i < HISTORY; i++) {
            SEQ[i] = 0;
            HIST_POS[i] = null;
            HIST_VEL[i] = null;
            HIST_THRUST[i] = null;
        }
        tickSeq = 0;
        lastSeq = 0;
        corrections = 0;
        lastCorrectionError = 0.0;
        unmatchedStreak = 0;
        localVelValid = false;
    }

    public static int lastSeq() {
        return lastSeq;
    }

    public static long corrections() {
        return corrections;
    }

    public static double lastCorrectionError() {
        return lastCorrectionError;
    }

    public static int lastMatchOffset() {
        return lastMatchOffset;
    }

    /** 客户端本地一步：积分 + 移动 + 记录历史。 */
    public static void tick(net.minecraft.world.entity.player.Player self, EntityRotation rot, int mask) {
        double[] thrust = new double[3];
        if (!localVelValid) {
            net.minecraft.world.phys.Vec3 v = self.getDeltaMovement();
            LOCAL_VEL[0] = v.x;
            LOCAL_VEL[1] = v.y;
            LOCAL_VEL[2] = v.z;
            localVelValid = true;
        }
        OrbitalBody.step(self, rot, mask, thrust, LOCAL_VEL);

        tickSeq++;
        lastSeq = tickSeq;
        int idx = tickSeq & (HISTORY - 1);
        SEQ[idx] = tickSeq;
        HIST_POS[idx] = new double[]{self.getX(), self.getY(), self.getZ()};
        HIST_VEL[idx] = new double[]{LOCAL_VEL[0], LOCAL_VEL[1], LOCAL_VEL[2]};
        HIST_THRUST[idx] = thrust;
    }

    /** 收到服务器权威状态：对账，必要时回滚重放。 */
    public static void onServerState(PlayerOrbitStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!Gravity.enabled()) return;

        if (payload.reset()) {
            // 基准状态：本地 tick 计数对齐到服务器最后处理的输入序号，历史全部作废
            tickSeq = Math.max(0, payload.seq());
            lastSeq = tickSeq;
            clearHistory();
            setLocalVelocity(payload.vx(), payload.vy(), payload.vz());
            snap(player, payload.x(), payload.y(), payload.z(),
                    payload.vx(), payload.vy(), payload.vz());
            return;
        }

        // 找一个最匹配的历史序号：服务器状态标的是"它刚消费的那个输入序号"，而客户端与
        // 服务器之间恒存在一个固定的一 tick 流水线偏移（服务器在同一 tick 内先跑实体、后收包），
        // 所以允许 seq-2..seq+2 的偏移，取误差最小者。偏移固定时误差会降到数值噪声，不再纠正。
        int bestSeq = Integer.MIN_VALUE;
        double bestError = Double.MAX_VALUE;
        for (int d = -2; d <= 2; d++) {
            int s = payload.seq() + d;
            if (s < 0) continue;
            int i = s & (HISTORY - 1);
            if (SEQ[i] != s || HIST_POS[i] == null) continue;
            double[] h = HIST_POS[i];
            double dx = h[0] - payload.x();
            double dy = h[1] - payload.y();
            double dz = h[2] - payload.z();
            double err = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (err < bestError) {
                bestError = err;
                bestSeq = s;
            }
        }
        if (bestSeq == Integer.MIN_VALUE) {            // 历史里没有对应 tick（重置过/太旧/中途上下载具）：连续太久对不上就无条件对齐一次，
            // 否则本地预测可能一直不受服务器约束地漂走。
            if (++unmatchedStreak > 40) {
                unmatchedStreak = 0;
                tickSeq = Math.max(0, payload.seq());
                lastSeq = tickSeq;
                clearHistory();
                setLocalVelocity(payload.vx(), payload.vy(), payload.vz());
                snap(player, payload.x(), payload.y(), payload.z(),
                        payload.vx(), payload.vy(), payload.vz());
            }
            return;
        }
        unmatchedStreak = 0;
        lastCorrectionError = bestError;
        lastMatchOffset = bestSeq - payload.seq();
        double threshold = SpaceSimulationConfig.orbitalCorrectionBlocks.get();
        if (bestError <= threshold) {
            return; // 预测与权威一致：保持本地预测，不打断画面
        }

        // 回滚到服务器状态（等价于客户端 bestSeq 时刻的状态），再用记录的推力重放到最新 seq
        double[] pos = {payload.x(), payload.y(), payload.z()};
        double[] vel = {payload.vx(), payload.vy(), payload.vz()};
        double[] outPos = new double[3];
        double[] outVel = new double[3];
        for (int s = bestSeq + 1; s <= tickSeq; s++) {
            int i = s & (HISTORY - 1);
            if (SEQ[i] != s || HIST_THRUST[i] == null) break;
            OrbitalPhysics.stepGravity(pos, vel, outPos, outVel);
            double refSpeed = OrbitalPhysics.speed(outVel);
            OrbitalPhysics.applyThrust(outVel, refSpeed, HIST_THRUST[i], FlightPhysics.MAX_SPEED);
            System.arraycopy(outPos, 0, pos, 0, 3);
            System.arraycopy(outVel, 0, vel, 0, 3);
            HIST_POS[i] = pos.clone();
            HIST_VEL[i] = vel.clone();
        }
        corrections++;
        setLocalVelocity(vel[0], vel[1], vel[2]);
        snap(player, pos[0], pos[1], pos[2], vel[0], vel[1], vel[2]);
    }

    /** 同步本地权威速度（重置/纠正后与服务器对齐）。 */
    private static void setLocalVelocity(double vx, double vy, double vz) {
        LOCAL_VEL[0] = vx;
        LOCAL_VEL[1] = vy;
        LOCAL_VEL[2] = vz;
        localVelValid = true;
    }

    private static void clearHistory() {
        for (int i = 0; i < HISTORY; i++) {
            SEQ[i] = 0;
            HIST_POS[i] = null;
            HIST_VEL[i] = null;
            HIST_THRUST[i] = null;
        }
    }

    /** 把本地玩家强制定位到权威状态（同时对齐上一 tick 位置，避免渲染插值拉出长线）。 */
    private static void snap(LocalPlayer player, double x, double y, double z,
                             double vx, double vy, double vz) {
        player.setPos(x, y, z);
        player.setDeltaMovement(vx, vy, vz);
        player.xo = x;
        player.yo = y;
        player.zo = z;
    }
}
