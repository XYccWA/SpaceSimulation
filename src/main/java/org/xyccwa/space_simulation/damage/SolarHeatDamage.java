package org.xyccwa.space_simulation.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.xyccwa.space_simulation.mixin.entity.LivingEntityDeathInvoker;
import org.xyccwa.space_simulation.network.SunRadiusPayload;
import org.xyccwa.space_simulation.util.SunRadius;

/**
 * 靠近太阳的高温伤害与表面处死（服务器权威，作用于所有生物实体）。
 *
 * 判定区域（半径取自 {@link SunRadius}，即渲染出的太阳表面）：
 * - 球壳 {@code (R, 1.1R]}：高温区，每 {@link #HEAT_DAMAGE_INTERVAL} tick 结算一次伤害，
 *   越靠近表面伤害越高（外缘 {@link #MIN_HEAT_DAMAGE} → 表面 {@link #MAX_HEAT_DAMAGE}，按接近度平方增长）；
 * - 球体 {@code d <= R}：触碰太阳表面，强制处死。先走 {@code hurt(Float.MAX_VALUE)}
 *   （普通伤害流程，含死亡消息与掉落），对创造/旁观模式等无敌情形由
 *   {@link LivingEntityDeathInvoker} 直接触发完整死亡流程。
 *
 * 区域厚度是太阳半径的固定比例，不单独配置：太阳半径变了，高温区随之等比缩放。
 */
public class SolarHeatDamage {

    private static final ResourceKey<DamageType> SOLAR_HEAT_KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space_simulation", "solar_heat"));

    /** 高温区厚度：太阳半径的比例（表面向外 10%） */
    private static final double HEAT_ZONE_FRACTION = 0.10;
    /** 高温伤害结算间隔（tick，10 = 0.5 秒） */
    private static final int HEAT_DAMAGE_INTERVAL = 10;
    /** 高温区外缘伤害（点） */
    private static final float MIN_HEAT_DAMAGE = 1.0F;
    /** 贴近太阳表面的伤害（点） */
    private static final float MAX_HEAT_DAMAGE = 20.0F;

    private DamageSource solarHeatSource(LivingEntity entity) {
        return new DamageSource(
                entity.level().registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(SOLAR_HEAT_KEY)
        );
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;   // 只在服务端判定
        if (!entity.isAlive()) return;

        MinecraftServer server = entity.getServer();
        if (server == null) return;

        final double radius = SunRadius.forServer(server);
        if (radius <= 0) return;

        final double x = entity.getX();
        final double y = entity.getY();
        final double z = entity.getZ();
        final double distSq = x * x + y * y + z * z;

        // 触碰/进入太阳表面：强制处死
        if (distSq <= radius * radius) {
            kill(entity);
            return;
        }

        // 高温区：表面向外 HEAT_ZONE_FRACTION × 半径
        final double outer = radius * (1.0 + HEAT_ZONE_FRACTION);
        if (distSq >= outer * outer) return;
        if (entity.tickCount % HEAT_DAMAGE_INTERVAL != 0) return;

        // 接近度：0 = 高温区外缘，1 = 太阳表面
        final double closeness = (outer - Math.sqrt(distSq)) / (outer - radius);
        final float damage = (float) (MIN_HEAT_DAMAGE
                + (MAX_HEAT_DAMAGE - MIN_HEAT_DAMAGE) * closeness * closeness);
        entity.hurt(solarHeatSource(entity), damage);
    }

    /** 强制处死：普通伤害流程失败（创造/旁观等无敌）时直接触发死亡流程。 */
    private void kill(LivingEntity entity) {
        DamageSource source = solarHeatSource(entity);
        if (entity.hurt(source, Float.MAX_VALUE)) return;
        if (!entity.isAlive()) return;
        ((LivingEntityDeathInvoker) entity).spaceSim$invokeDie(source);
    }

    /** 玩家登录时下发当前世界的太阳半径：客户端渲染球面与服务器判定必须同源。 */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PacketDistributor.sendToPlayer(player, new SunRadiusPayload(SunRadius.forServer(server)));
    }
}
