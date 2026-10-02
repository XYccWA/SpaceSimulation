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
import org.xyccwa.space_simulation.orbital.PlayerOrbitServer;

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
                    // 服务器权威轨道物理：记下本 tick 的输入与序号（服务器自己积分，位置不再信客户端）
                    PlayerOrbitServer.onControlPacket(sp, payload.moveMask(), payload.seq());
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

        // 服务器 -> 客户端：当前世界的太阳半径（渲染球面与高温判定同源）
        registrar.playToClient(SunRadiusPayload.TYPE, SunRadiusPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> org.xyccwa.space_simulation.client.WorldSphereRenderer
                        .setSyncedRadius(payload.radius())));

        // 服务器 -> 客户端：轨道力学参数（客户端预测必须与服务器用同一个 μ）
        registrar.playToClient(OrbitalParamsPayload.TYPE, OrbitalParamsPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> org.xyccwa.space_simulation.orbital.Gravity
                        .setMu(payload.enabled() ? payload.mu() : 0.0)));

        // 服务器 -> 客户端：本玩家的权威运动状态（位置 + 速度 + 输入序号），客户端据此对账
        registrar.playToClient(PlayerOrbitStatePayload.TYPE, PlayerOrbitStatePayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> org.xyccwa.space_simulation.client.PlayerOrbitClient.onServerState(payload)));
    }
}
