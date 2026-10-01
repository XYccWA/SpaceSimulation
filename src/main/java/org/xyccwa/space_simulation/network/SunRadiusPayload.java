package org.xyccwa.space_simulation.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.xyccwa.space_simulation.SpaceSimulation;

/**
 * 服务器 -> 客户端：当前世界的太阳半径（格）。
 * 客户端渲染的球面半径与服务器高温区/处死判定共用该值，避免两者错位。
 */
public record SunRadiusPayload(double radius) implements CustomPacketPayload {
    public static final Type<SunRadiusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SpaceSimulation.MOD_ID, "sun_radius"));

    public static final StreamCodec<ByteBuf, SunRadiusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SunRadiusPayload::radius,
            SunRadiusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
