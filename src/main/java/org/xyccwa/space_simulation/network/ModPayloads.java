package org.xyccwa.space_simulation.network;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.api.EntityRotation;
import org.xyccwa.space_simulation.damage.PlayerAccelerationDamage;

/** 自定义包注册与处理。 */
public final class ModPayloads {
    private ModPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SpaceSimulation.MOD_ID).versioned("1").optional();

        // 客户端 -> 服务器：每 tick 的控制输入
        registrar.playToServer(PlayerControlPayload.TYPE, PlayerControlPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Player player = ctx.player();
                if (player instanceof EntityRotation rot) {
                    rot.setOrientation(payload.orientation());
                    rot.setMoveMask(payload.moveMask());
                }
                // 客户端本地权威速度差分出的运动指标 → 服务器据此判加速度/撞击伤害
                if (player instanceof ServerPlayer sp) {
                    PlayerAccelerationDamage.reportMotion(sp, payload.smoothedAccelMPS2(), payload.impactDeltaVBlocksPerTick());
                    // 广播该玩家的朝向给所有追踪者（不含自己：本地玩家朝向由本地模拟维护，
                    // 回显会覆盖本地实时四元数导致插值回跳）
                    PacketDistributor.sendToPlayersTrackingEntity(sp,
                            new PlayerOrientationPayload(sp.getId(), payload.orientation()));
                }
            });
        });

        // 服务器 -> 客户端：其他玩家的朝向
        registrar.playToClient(PlayerOrientationPayload.TYPE, PlayerOrientationPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    Entity entity = mc.level.getEntity(payload.entityId());
                    // 跳过本地玩家：本地朝向由本地模拟维护，不接收回显
                    if (entity != null && entity != mc.player && entity instanceof EntityRotation rot) {
                        rot.setOrientation(payload.orientation());
                    }
                }
            });
        });
    }
}
