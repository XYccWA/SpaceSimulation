package org.xyccwa.space_simulation.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;
import org.xyccwa.space_simulation.player.PlayerAcceleration;
import org.xyccwa.space_simulation.util.AccelerationData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerAccelerationDamage {

    // 加速度伤害阈值配置（单位：米/秒²）
    private static final double HIGH_G_FORCE_THRESHOLD = SpaceSimulationConfig.highGravityAccelerationThreshold.get();      // 10G - 开始受到high_g_force伤害
    private static final double SUSTAINED_G_THRESHOLD = SpaceSimulationConfig.sustainedGThreshold.get();      // 5G - 持续超过阈值会受伤
    private static final int SUSTAINED_G_DURATION = SpaceSimulationConfig.sutainedGDuration.get();           // 持续超过阈值的tick数（1秒）

    // 伤害类型 ResourceKey
    private static final ResourceKey<DamageType> HIGH_G_FORCE_KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space_simulation", "high_g_force"));

    private static final ResourceKey<DamageType> SUSTAINED_G_FORCE_KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space_simulation", "sustained_g_force"));

    // 用于存储玩家持续高加速度的计时器
    private static final Map<UUID, AccelerationData> accelerationTimer = new ConcurrentHashMap<>();

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

        // 获取当前加速度（米/秒²）
        double acceleration = PlayerAcceleration.getCurrentAcceleration(player).length() * 400;

        // 层级1：超高G力 - 每tick直接造成伤害
        if (acceleration >= HIGH_G_FORCE_THRESHOLD) {
            applyHighGForceDamage(serverPlayer, acceleration);
            resetSustainedTimer(serverPlayer);
            return;
        }

        // 层级2：持续高G力 - 超过阈值一段时间后造成伤害
        if (acceleration >= SUSTAINED_G_THRESHOLD) {
            handleSustainedGForce(serverPlayer, acceleration);
        } else {
            decreaseSustainedTimer(serverPlayer);
        }
    }

    /**
     * 应用超高G力伤害（每tick直接伤害）
     */
    private void applyHighGForceDamage(ServerPlayer player, double acceleration) {
        // 计算伤害：加速度越高伤害越大
        float excessG = (float) ((acceleration - HIGH_G_FORCE_THRESHOLD) / 9.81);
        float damage = 2.0f + excessG * 0.5f;
        damage = Math.min(damage, 20.0f);

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
        accelerationTimer.remove(player.getUUID());
    }
}
