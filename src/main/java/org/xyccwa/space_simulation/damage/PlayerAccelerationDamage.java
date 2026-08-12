package org.xyccwa.space_simulation.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;
import org.xyccwa.space_simulation.util.AccelerationData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加速度/撞击伤害判定（服务器权威）。
 *
 * 数据来源：客户端本地玩家每 tick 用权威 deltaMovement 算出两个指标，随 PlayerControlPayload 上报：
 * - {@code smoothedAccelMPS2}：EMA 平滑加速度（m/s²），用于持续过载检测。
 * - {@code impactDeltaVBlocksPerTick}：本 tick 速度大小骤降量（方块/tick），用于撞击检测。
 *
 * 这规避了旧实现的两个问题：
 * - 旧实现服务器用"位置二阶差分"算加速度，对移动包相位差极敏感，匀速飞行也会测出瞬时假尖峰。
 * - 旧的 EMA + 连续 3 tick 去抖把真实的单 tick 高速撞击也挡掉了。
 * 现在撞击走独立冲击路径（单 tick 瞬态事件，无平滑、无去抖），持续过载才用平滑 + 去抖窗口。
 * 客户端只上报速度派生指标，伤害判定仍在本类（服务器权威），单机/多人经同一网络通道统一生效。
 */
public class PlayerAccelerationDamage {

    // 伤害类型 ResourceKey
    private static final ResourceKey<DamageType> HIGH_G_FORCE_KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space_simulation", "high_g_force"));

    private static final ResourceKey<DamageType> SUSTAINED_G_FORCE_KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space_simulation", "sustained_g_force"));

    // 持续过载阈值配置（m/s²）
    private static final double HIGH_G_FORCE_THRESHOLD = SpaceSimulationConfig.highGravityAccelerationThreshold.get();      // 30G
    private static final double SUSTAINED_G_THRESHOLD = SpaceSimulationConfig.sustainedGThreshold.get();      // 10G
    private static final int SUSTAINED_G_DURATION = SpaceSimulationConfig.sutainedGDuration.get();           // 持续超阈值tick数

    // 撞击冲击阈值：单 tick 速度大小骤降量（方块/tick）。飞行加速是渐进的（24 m/s² = 0.06 方块/tick），
    // 阈值 1.0 = 单 tick 减速 20 m/s，远高于正常加速噪声，只有撞击/急停才够得着。
    private static final double IMPACT_DELTA_V_THRESHOLD = 1.0;
    // 撞击伤害：damage = 1 + (deltaV - threshold) * 系数。200 m/s（10 方块/tick）骤停 → 1 + 9 = 10。
    private static final double IMPACT_DAMAGE_PER_EXTRA_DELTA_V = 1.0;
    private static final float MAX_IMPACT_DAMAGE = 20.0f;

    // 客户端每 tick 上报的运动指标 [smoothedAccelMPS2, impactDeltaVBlocksPerTick]
    private static final Map<UUID, double[]> reportedMotion = new ConcurrentHashMap<>();

    // 持续高G 连续超阈值 tick 数（去抖）：瞬时突变只在 1~2 tick 内，不应触发持续伤害；真实持续过载才生效
    private static final Map<UUID, Integer> highGOverTicks = new ConcurrentHashMap<>();
    private static final int MIN_HIGH_G_TICKS = 3;

    // 持续超阈值计时器
    private static final Map<UUID, AccelerationData> accelerationTimer = new ConcurrentHashMap<>();

    /** 客户端每 tick 上报本玩家运动指标（由 ModPayloads 的 PlayerControlPayload 处理调用）。 */
    public static void reportMotion(ServerPlayer player, double smoothedAccelMPS2, double impactDeltaVBlocksPerTick) {
        reportedMotion.put(player.getUUID(), new double[]{smoothedAccelMPS2, impactDeltaVBlocksPerTick});
    }

    /**
     * 动态获取高G力伤害源
     */
    private DamageSource getHighGForceDamage(Player player) {
        return new DamageSource(
                player.level().registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(HIGH_G_FORCE_KEY)
        );
    }

    /**
     * 动态获取持续G力伤害源
     */
    private DamageSource getSustainedGForceDamage(Player player) {
        return new DamageSource(
                player.level().registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(SUSTAINED_G_FORCE_KEY)
        );
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        if (player.level().isClientSide) return; // 只在服务端处理伤害
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID id = serverPlayer.getUUID();
        double[] motion = reportedMotion.get(id);
        if (motion == null) return; // 尚未收到首包（客户端还没上报），不判伤
        double smoothedAccel = motion[0];
        double impactDeltaV = motion[1];

        // 路径1：撞击冲击 —— 单 tick 速度骤降（撞击/急停）。瞬态事件，无 EMA 稀释、无连续 tick 去抖。
        if (impactDeltaV > IMPACT_DELTA_V_THRESHOLD) {
            applyImpactDamage(serverPlayer, impactDeltaV);
        }

        // 路径2：持续过载 —— 平滑加速度连续超阈值 MIN_HIGH_G_TICKS tick 后才每 tick 伤害（去抖）
        if (smoothedAccel >= HIGH_G_FORCE_THRESHOLD) {
            int count = highGOverTicks.merge(id, 1, Integer::sum);
            if (count >= MIN_HIGH_G_TICKS) {
                applyHighGForceDamage(serverPlayer, smoothedAccel);
            }
            resetSustainedTimer(serverPlayer);
            return;
        }
        highGOverTicks.remove(id);

        // 层级2：持续高G力 - 超过阈值一段时间后造成伤害
        if (smoothedAccel >= SUSTAINED_G_THRESHOLD) {
            handleSustainedGForce(serverPlayer, smoothedAccel);
        } else {
            decreaseSustainedTimer(serverPlayer);
        }
    }

    /** 玩家登出时清理其上报数据与计时器（防长期服务器内存泄漏）。 */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            cleanupPlayer(event.getEntity());
        }
    }

    /**
     * 撞击伤害：按单 tick 速度骤降量计算。高速撞击 = 大减速 = 大伤害。
     */
    private void applyImpactDamage(ServerPlayer player, double impactDeltaV) {
        float damage = (float) (1.0 + (impactDeltaV - IMPACT_DELTA_V_THRESHOLD) * IMPACT_DAMAGE_PER_EXTRA_DELTA_V);
        damage = Math.min(damage, MAX_IMPACT_DAMAGE);
        player.hurt(getHighGForceDamage(player), damage);
    }

    /**
     * 应用超高G力伤害（每tick直接伤害）
     */
    private void applyHighGForceDamage(ServerPlayer player, double acceleration) {
        // 计算伤害：加速度越高伤害越大
        float excessG = (float) ((acceleration - HIGH_G_FORCE_THRESHOLD) / 9.81);
        float damage = 1.0f + excessG * 0.15f;
        damage = Math.min(damage, 6.0f);

        player.hurt(getHighGForceDamage(player), damage);
    }

    /**
     * 处理持续高G力伤害
     */
    private void handleSustainedGForce(ServerPlayer player, double acceleration) {
        UUID playerId = player.getUUID();
        AccelerationData data = accelerationTimer.computeIfAbsent(playerId,
                k -> new AccelerationData(acceleration));

        if (acceleration > data.getLastAcceleration()) {
            data.resetTimer();
        }

        data.setLastAcceleration(acceleration);
        data.incrementDuration();

        int duration = data.getDuration();

        if (duration >= SUSTAINED_G_DURATION) {
            applySustainedDamage(player, acceleration, duration);
        }
    }

    /**
     * 应用持续高G力伤害
     */
    private void applySustainedDamage(ServerPlayer player, double acceleration, int duration) {
        float damage = 0.5f;
        float excessDuration = (float) ((duration - SUSTAINED_G_DURATION) / 20.0f);
        damage += excessDuration * 0.5f;
        float excessG = (float) ((acceleration - SUSTAINED_G_THRESHOLD) / 9.81);
        damage += excessG * 0.2f;
        damage = Math.min(Math.max(damage, 0.5f), 8.0f);

        if (duration % 10 == 0) {
            player.hurt(getSustainedGForceDamage(player), damage);
        }
    }

    /**
     * 减少持续计时器
     */
    private void decreaseSustainedTimer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        AccelerationData data = accelerationTimer.get(playerId);

        if (data != null) {
            int newDuration = Math.max(0, data.getDuration() - 2);
            if (newDuration == 0) {
                accelerationTimer.remove(playerId);
            } else {
                data.setDuration(newDuration);
                data.setLastAcceleration(0);
            }
        }
    }

    /**
     * 重置持续计时器
     */
    private void resetSustainedTimer(ServerPlayer player) {
        accelerationTimer.remove(player.getUUID());
    }

    /**
     * 清理玩家数据（在玩家退出时调用）
     */
    public static void cleanupPlayer(Player player) {
        UUID id = player.getUUID();
        reportedMotion.remove(id);
        accelerationTimer.remove(id);
        highGOverTicks.remove(id);
    }
}
