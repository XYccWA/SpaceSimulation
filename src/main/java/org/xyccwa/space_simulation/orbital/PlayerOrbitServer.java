package org.xyccwa.space_simulation.orbital;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.api.EntityRotation;
import org.xyccwa.space_simulation.diagnostic.InteractionDiagnostics;
import org.xyccwa.space_simulation.interaction.InteractionTimeline;
import org.xyccwa.space_simulation.network.PlayerOrbitStatePayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端权威的轨道物理。
 *
 * 每 tick 在 {@code Player.travel} 内跑一次数值积分（用客户端上报的输入掩码与朝向），
 * 直接移动服务端实体并把结果下发给出该玩家自己的客户端（PlayerOrbitStatePayload）。
 * 客户端的移动包被忽略（ServerGamePacketListenerImplMixin），位置一律以本类为准。
 *
 * 为什么双端都要跑积分：客户端预测能让输入零延迟，服务器权威能防作弊并让其它玩家
 * 看到真实位置。两端用同一份 OrbitalPhysics/Gravity + 同一 seq 对账，误差超阈值才回滚。
 */
public final class PlayerOrbitServer {

    /** 玩家 UUID → 最近一次控制包里的输入序号（原样回传给客户端用于对账）。 */
    private static final Map<UUID, Integer> INPUT_SEQ = new ConcurrentHashMap<>();
    /** 玩家 UUID → 最近一次控制包里的输入掩码。 */
    private static final Map<UUID, Integer> MOVE_MASK = new ConcurrentHashMap<>();
    /** 需要下发一次"基准状态"（登录/重生/传送后）的玩家。 */
    private static final Map<UUID, Boolean> PENDING_RESET = new ConcurrentHashMap<>();
    /** 玩家 UUID → 本 tick 积分出的权威位置（用于在连接 tick 末尾覆盖原版的回滚）。 */
    private static final Map<UUID, double[]> AUTHORITATIVE_POS = new ConcurrentHashMap<>();
    /** 玩家 UUID → 该权威位置对应的 game time。 */
    private static final Map<UUID, Long> AUTHORITATIVE_TICK = new ConcurrentHashMap<>();
    /** 玩家 UUID → 权威速度（块/tick；不依赖实体 deltaMovement，见 OrbitalBody.step 注释）。 */
    private static final Map<UUID, double[]> AUTHORITATIVE_VEL = new ConcurrentHashMap<>();

    private PlayerOrbitServer() {
    }

    // ------------------------------------------------------------ 生命周期

    public static void onControlPacket(ServerPlayer player, int moveMask, int seq) {
        UUID id = player.getUUID();
        MOVE_MASK.put(id, moveMask);
        INPUT_SEQ.put(id, seq);
    }

    /** 登录/重生/传送后用：下一条状态包带 reset 标记，客户端无条件对齐并清空预测历史。 */
    public static void markReset(Player player) {
        if (player != null) PENDING_RESET.put(player.getUUID(), Boolean.TRUE);
    }

    public static void forget(Player player) {
        if (player == null) return;
        UUID id = player.getUUID();
        INPUT_SEQ.remove(id);
        MOVE_MASK.remove(id);
        PENDING_RESET.remove(id);
        AUTHORITATIVE_POS.remove(id);
        AUTHORITATIVE_TICK.remove(id);
        AUTHORITATIVE_VEL.remove(id);
        InteractionTimeline.forget(player);
        InteractionDiagnostics.forget(player);
    }

    private static boolean consumeReset(Player player) {
        return PENDING_RESET.remove(player.getUUID()) != null;
    }

    // ------------------------------------------------------------ 状态查询

    public static int moveMaskOf(Player player) {
        Integer m = MOVE_MASK.get(player.getUUID());
        return m == null ? 0 : m;
    }

    public static int inputSeqOf(Player player) {
        Integer s = INPUT_SEQ.get(player.getUUID());
        return s == null ? -1 : s;
    }

    /**
     * 该玩家是否由本系统做权威模拟：开了引力、在主世界、且是正常飞行状态
     * （乘客/死亡/观察者走原版，避免破坏载具与旁观）。
     */
    public static boolean isActive(Player player) {
        if (player == null || !Gravity.enabled()) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (level.dimension() != Level.OVERWORLD) return false;
        return !player.isPassenger() && !player.isDeadOrDying() && !player.isSpectator();
    }

    // ------------------------------------------------------------ 每 tick 积分

    /**
     * 服务端一步：积分 → 移动 → 同步权威状态。调用方需保证玩家处于 {@link #isActive} 状态。
     */
    public static void tick(Player self, EntityRotation rot) {
        double[] thrust = new double[3];
        double beforeX = self.getX(), beforeY = self.getY(), beforeZ = self.getZ();
        double[] vel = velocityOf(self);
        OrbitalBody.step(self, rot, moveMaskOf(self), thrust, vel);

        if (self instanceof ServerPlayer sp) {
            // 位置由服务器自己算，原版 handleMovePlayer 不再执行；区块跟踪与"已知移动"
            // 原本由它顺带更新，这里补上，否则飞出去不加载新区块、生物 AI 也感知不到移动。
            if (sp.serverLevel() != null) {
                sp.serverLevel().getChunkSource().move(sp);
            }
            sp.setKnownMovement(new Vec3(sp.getX() - beforeX, sp.getY() - beforeY, sp.getZ() - beforeZ));
            rememberAuthoritative(sp);
            // 延迟补偿用：服务端权威位置历史（每 tick 一行，供交互判定回溯到客户端时间轴）
            InteractionTimeline.recordAuthoritative(sp);
            sendState(sp, consumeReset(sp));
        }
    }

    /** 取该玩家的权威速度（首次使用时用实体当前速度初始化）。 */
    private static double[] velocityOf(Player player) {
        double[] vel = AUTHORITATIVE_VEL.get(player.getUUID());
        if (vel == null) {
            Vec3 v = player.getDeltaMovement();
            vel = new double[]{v.x, v.y, v.z};
            AUTHORITATIVE_VEL.put(player.getUUID(), vel);
        }
        return vel;
    }

    /** 覆盖权威速度（归位/传送/重生等由外部决定速度的场合）。 */
    public static void setAuthoritativeVelocity(Player player, double vx, double vy, double vz) {
        double[] vel = AUTHORITATIVE_VEL.get(player.getUUID());
        if (vel == null) {
            AUTHORITATIVE_VEL.put(player.getUUID(), new double[]{vx, vy, vz});
        } else {
            vel[0] = vx;
            vel[1] = vy;
            vel[2] = vz;
        }
    }

    /** 记下本 tick 积分出的权威位置（连接 tick 末尾要覆盖原版回滚）。 */
    private static void rememberAuthoritative(ServerPlayer player) {
        UUID id = player.getUUID();
        AUTHORITATIVE_POS.put(id, new double[]{player.getX(), player.getY(), player.getZ()});
        if (player.level() != null) {
            AUTHORITATIVE_TICK.put(id, player.level().getGameTime());
        }
    }

    /**
     * 覆盖原版的位置回滚 —— 这是服务器权威能成立的关键。
     *
     * 原版 {@code ServerGamePacketListenerImpl.tick()} 的顺序是：
     *   resetPosition()（记下 firstGood=当前位置）→ player.doTick()（我们的积分在这里跑）
     *   → player.absMoveTo(firstGood)：把位置写回 tick 之前的值，等着客户端移动包来"确认"。
     * 也就是说原版根本不认服务器自己算出来的位移。必须在连接 tick 结束时把权威位置再写回去，
     * 否则每 tick 积分出的位移都会被抹掉（表现为玩家原地抖动、位置钉死）。
     *
     * 只在"本 tick 刚积分过"（game time 相同）时覆盖，避免抹掉传送等其它位置变更。
     */
    public static void reassertAuthoritative(ServerPlayer player) {
        if (!isActive(player)) return;
        UUID id = player.getUUID();
        double[] pos = AUTHORITATIVE_POS.get(id);
        Long tick = AUTHORITATIVE_TICK.get(id);
        if (pos == null || tick == null) return;
        if (player.level() == null || player.level().getGameTime() != tick) return;
        if (player.getX() != pos[0] || player.getY() != pos[1] || player.getZ() != pos[2]) {
            player.setPos(pos[0], pos[1], pos[2]);
        }
    }

    /** 下发权威状态（位置 + 速度 + 输入序号）。 */
    public static void sendState(ServerPlayer player, boolean reset) {
        double[] v = velocityOf(player);
        PacketDistributor.sendToPlayer(player, new PlayerOrbitStatePayload(
                player.getX(), player.getY(), player.getZ(),
                v[0], v[1], v[2],
                inputSeqOf(player), reset));
    }

    /** 把玩家放到指定位置与速度并下发基准状态（登录传播、传送、重生都用它对齐两端）。 */
    public static void placeAt(ServerPlayer player, double x, double y, double z,
                               double vx, double vy, double vz) {
        player.moveTo(x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(vx, vy, vz);
        setAuthoritativeVelocity(player, vx, vy, vz);
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        if (player.serverLevel() != null) {
            player.serverLevel().getChunkSource().move(player);
        }
        rememberAuthoritative(player);
        markReset(player);
        SpaceSimulation.LOGGER.info("[轨道] 归位 {} → ({}, {}, {}) v=({}, {}, {})",
                player.getName().getString(), (long) x, (long) y, (long) z, vx, vy, vz);
    }
}
