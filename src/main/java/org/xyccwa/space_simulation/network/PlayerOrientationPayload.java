package org.xyccwa.space_simulation.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.xyccwa.space_simulation.SpaceSimulation;

/**
 * 服务器 -> 客户端，广播某玩家实体的最新朝向四元数。
 * 其他客户端据此让该玩家的模型/相机插值跟随。
 */
public record PlayerOrientationPayload(int entityId, Quaternionf orientation) implements CustomPacketPayload {
    public static final Type<PlayerOrientationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SpaceSimulation.MOD_ID, "player_orientation"));

    public static final StreamCodec<ByteBuf, PlayerOrientationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlayerOrientationPayload::entityId,
            QuaternionfStreamCodec.INSTANCE, PlayerOrientationPayload::orientation,
            PlayerOrientationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
