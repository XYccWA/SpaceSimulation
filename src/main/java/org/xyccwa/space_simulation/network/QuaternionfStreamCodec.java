package org.xyccwa.space_simulation.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Quaternionf;

/** 四元数 = 4 个 float，直接写入缓冲区。 */
public final class QuaternionfStreamCodec implements StreamCodec<ByteBuf, Quaternionf> {
    public static final QuaternionfStreamCodec INSTANCE = new QuaternionfStreamCodec();

    private QuaternionfStreamCodec() {
    }

    @Override
    public Quaternionf decode(ByteBuf buf) {
        return new Quaternionf(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public void encode(ByteBuf buf, Quaternionf q) {
        buf.writeFloat(q.x);
        buf.writeFloat(q.y);
        buf.writeFloat(q.z);
        buf.writeFloat(q.w);
    }
}
