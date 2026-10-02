package org.xyccwa.space_simulation.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.xyccwa.space_simulation.SpaceSimulation;

/**
 * 客户端 -> 服务器，每 tick 发送玩家朝向四元数、移动输入掩码、本地算好的运动指标，
 * 以及本 tick 的输入序号 seq。
 *
 * seq 是客户端"本地物理 tick"的递增计数：服务器用它积分，并把结果原样回传，
 * 客户端据此在本地预测历史里找到对应时刻的状态做比对/回滚重放（服务器权威）。
 * smoothedAccel/impactDeltaV 由客户端本地速度差分得到，服务器据此判加速度/撞击伤害。
 */
public record PlayerControlPayload(Quaternionf orientation, int moveMask, float smoothedAccelMPS2,
                                   float impactDeltaVBlocksPerTick, int seq) implements CustomPacketPayload {
    public static final Type<PlayerControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SpaceSimulation.MOD_ID, "player_control"));

    public static final StreamCodec<ByteBuf, PlayerControlPayload> STREAM_CODEC = StreamCodec.composite(
            QuaternionfStreamCodec.INSTANCE, PlayerControlPayload::orientation,
            ByteBufCodecs.VAR_INT, PlayerControlPayload::moveMask,
            ByteBufCodecs.FLOAT, PlayerControlPayload::smoothedAccelMPS2,
            ByteBufCodecs.FLOAT, PlayerControlPayload::impactDeltaVBlocksPerTick,
            ByteBufCodecs.VAR_INT, PlayerControlPayload::seq,
            PlayerControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
