package org.xyccwa.space_simulation.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.xyccwa.space_simulation.SpaceSimulation;

/**
 * 服务器 -> 客户端：轨道力学参数（是否启用 + 引力参数 μ）。
 *
 * 玩家/实体物理是"双端跑同一套积分、服务器权威"：客户端必须拿到与服务器完全相同的 μ，
 * 否则预测与权威值会持续发散（多人游戏里客户端读的是自己的配置文件，不可信）。
 */
public record OrbitalParamsPayload(boolean enabled, double mu) implements CustomPacketPayload {
    public static final Type<OrbitalParamsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SpaceSimulation.MOD_ID, "orbital_params"));

    public static final StreamCodec<ByteBuf, OrbitalParamsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OrbitalParamsPayload::enabled,
            ByteBufCodecs.DOUBLE, OrbitalParamsPayload::mu,
            OrbitalParamsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
