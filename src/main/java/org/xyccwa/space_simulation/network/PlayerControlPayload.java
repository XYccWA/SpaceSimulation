package org.xyccwa.space_simulation.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.xyccwa.space_simulation.SpaceSimulation;

/**
 * 客户端 -> 服务器，每 tick 发送玩家朝向四元数、移动输入掩码，以及本地算好的运动指标。
 * 服务器据此同步朝向，并据运动指标判加速度/撞击伤害（客户端本地速度权威，无移动包相位噪声）。
 */
public record PlayerControlPayload(Quaternionf orientation, int moveMask, float smoothedAccelMPS2, float impactDeltaVBlocksPerTick) implements CustomPacketPayload {
    public static final Type<PlayerControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SpaceSimulation.MOD_ID, "player_control"));

    public static final StreamCodec<ByteBuf, PlayerControlPayload> STREAM_CODEC = StreamCodec.composite(
            QuaternionfStreamCodec.INSTANCE, PlayerControlPayload::orientation,
            ByteBufCodecs.VAR_INT, PlayerControlPayload::moveMask,
            ByteBufCodecs.FLOAT, PlayerControlPayload::smoothedAccelMPS2,
            ByteBufCodecs.FLOAT, PlayerControlPayload::impactDeltaVBlocksPerTick,
            PlayerControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
