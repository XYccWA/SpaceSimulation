package org.xyccwa.space_simulation.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端本地玩家的运动数据（每 tick 由 ClientTickHandler 更新）。
 *
 * 数据源是 LocalPlayer 的 {@code deltaMovement}（本地权威速度），而非位置差分：
 * - 无移动包相位噪声：服务器端"位置二阶差分"对移动包到达时机极敏感，匀速飞行也会测出假尖峰；
 *   客户端本地每 tick 更新一次速度，差分干净。
 * - 免疫服务器位置校正：位置同步/传送改的是 position，不改 deltaMovement，不会产生虚假速度突变。
 *
 * 产出两类指标，随 PlayerControlPayload 上报服务器判伤：
 * - {@code smoothedAcceleration}（m/s²）：EMA 平滑加速度，用于持续过载检测。
 * - {@code impactDeltaV}（方块/tick）：本 tick 速度大小骤降量，用于撞击检测（单 tick 瞬态，不去抖）。
 */
public class PlayerAcceleration {
    // 客户端本地只有本地玩家，但保留按 Player 存以兼容调用方
    private static final Map<Player, Vec3> lastVelocityMap = new ConcurrentHashMap<>();
    private static final Map<Player, Double> prevSpeedMap = new ConcurrentHashMap<>();
    private static final Map<Player, Double> smoothedAccelMap = new ConcurrentHashMap<>();
    private static final Map<Player, Double> impactDeltaVMap = new ConcurrentHashMap<>();
    private static final Map<Player, Vec3> rawAccelMap = new ConcurrentHashMap<>();

    private static final double ACCEL_SMOOTHING_ALPHA = 0.5;

    public static void updatePlayer(Player player) {
        if (player == null) {
            return;
        }

        // 速度：方块/tick（本地权威）
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.length();

        // 瞬时加速度：本 tick 速度差（方块/tick²）
        Vec3 lastVel = lastVelocityMap.get(player);
        Vec3 rawAccel = Vec3.ZERO;
        if (lastVel != null) {
            rawAccel = velocity.subtract(lastVel);
        }
        lastVelocityMap.put(player, velocity);
        rawAccelMap.put(player, rawAccel);

        // 持续过载指标：EMA 平滑后的加速度（m/s²）。真实持续高G 快速收敛到原值，噪声被衰减。
        double rawMPS2 = rawAccel.length() * 400.0;
        Double prev = smoothedAccelMap.get(player);
        double smoothed = (prev == null) ? rawMPS2 : prev + ACCEL_SMOOTHING_ALPHA * (rawMPS2 - prev);
        smoothedAccelMap.put(player, smoothed);

        // 撞击指标：本 tick 速度大小骤降量（方块/tick）。飞行加速是渐进的（0.06 方块/tick），
        // 单 tick 大幅减速必然是撞击/急停。
        double prevSpeed = prevSpeedMap.getOrDefault(player, speed);
        double deltaV = Math.max(0.0, prevSpeed - speed);
        prevSpeedMap.put(player, speed);
        impactDeltaVMap.put(player, deltaV);
    }

    /** 当前速度（方块/tick），F3 调试屏 ×20 显示为 m/s。 */
    public static Vec3 getCurrentVelocity(Player player) {
        return lastVelocityMap.getOrDefault(player, Vec3.ZERO);
    }

    /** 原始瞬时加速度（方块/tick²），F3 调试屏 ×400 显示为 m/s²（显示瞬时值，便于观察真实突变）。 */
    public static Vec3 getCurrentAcceleration(Player player) {
        return rawAccelMap.getOrDefault(player, Vec3.ZERO);
    }

    /** EMA 平滑加速度（m/s²），随 PlayerControlPayload 上报服务器判持续过载。 */
    public static double getSmoothedAcceleration(Player player) {
        return smoothedAccelMap.getOrDefault(player, 0.0);
    }

    /** 本 tick 速度骤降量（方块/tick），随 PlayerControlPayload 上报服务器判撞击伤害。 */
    public static double getImpactDeltaV(Player player) {
        return impactDeltaVMap.getOrDefault(player, 0.0);
    }

    public static void removePlayer(Player player) {
        lastVelocityMap.remove(player);
        prevSpeedMap.remove(player);
        smoothedAccelMap.remove(player);
        impactDeltaVMap.remove(player);
        rawAccelMap.remove(player);
    }
}
