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
import org.xyccwa.space_simulation.mixin.entity.LivingEntityDeathInvoker;
import org.xyccwa.space_simulation.player.PlayerSpawnPoint;

/**
 * 太阳警戒线：玩家进入太阳球体（球心世界原点，半径 = 警戒半径）内部即被处死。
 *
 * 处死策略（服务器权威）：
 * - 普通/创造模式：调用 {@code hurt(solar_burn, Float.MAX_VALUE)} 造成超额伤害。
 *   solar_burn 已加入 minecraft:bypasses_invulnerability 标签，创造模式的无敌免疫被绕过；
 * - 旁观模式：{@code hurt()} 因 isSpectator() 恒失败，改经 {@link LivingEntityDeathInvoker}
 *   直接调用 {@code LivingEntity.die()} 触发完整死亡流程。
 */
public class SunKillHandler {

    private static final ResourceKey<DamageType> SOLAR_BURN_KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space_simulation", "solar_burn"));

    /** 获取太阳灼烧伤害源（带 bypasses_invulnerability 标签，可伤创造模式）。 */
    private DamageSource getSolarBurnDamage(Player player) {
        return new DamageSource(
                player.level().registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(SOLAR_BURN_KEY)
        );
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;      // 只在服务端处理
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!serverPlayer.isAlive()) return;           // 已死亡/重生流程中不再重复触发
        if (PlayerSpawnPoint.isWithinSpawnGrace(serverPlayer)) return; // 登录/重生保护期，避免"登录即死/重生死循环"

        double killRadius = SpaceSimulationConfig.solarKillRadius.get();
        if (killRadius <= 0) return;

        // 太阳球心在世界原点：三维距离平方小于警戒半径平方 = 已进入太阳内部
        double x = serverPlayer.getX();
        double y = serverPlayer.getY();
        double z = serverPlayer.getZ();
        double distSq = x * x + y * y + z * z;
        if (distSq >= killRadius * killRadius) return;

        DamageSource source = getSolarBurnDamage(serverPlayer);
        if (!serverPlayer.hurt(source, Float.MAX_VALUE) && serverPlayer.isAlive()) {
            // hurt 无效（旁观模式等）→ 直接触发死亡流程
            ((LivingEntityDeathInvoker) serverPlayer).spaceSim$invokeDie(source);
        }
    }
}
