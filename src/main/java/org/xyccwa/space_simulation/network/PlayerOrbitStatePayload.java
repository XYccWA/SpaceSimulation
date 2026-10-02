package org.xyccwa.space_simulation.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.xyccwa.space_simulation.SpaceSimulation;

/**
 * 服务器 -> 客户端：该玩家的权威运动状态（位置 + 速度）。
 *
 * 服务器每 tick 跑一次积分并把结果下发；客户端本地也在预测（消除输入延迟），
 * 收到后用 seq 找到自己当时记录的同序号预测值比较：
 *   - 误差 ≤ 阈值：忽略（本地预测继续，画面无感）；
 *   - 误差 > 阈值：回滚到服务器状态并重放之后的输入（纠正作弊/丢包/碰撞分歧）。
 * reset = true 表示这是登录/重生/传送后的基准状态，客户端必须无条件对齐并清空历史。
 */
public record PlayerOrbitStatePayload(double x, double y, double z,
                                      double vx, double vy, double vz,
                                      int seq, boolean reset) implements CustomPacketPayload {
    public static final Type<PlayerOrbitStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SpaceSimulation.MOD_ID, "player_orbit_state"));

    // composite 最多 6 个分量，这里 9 个，手写编解码
    public static final StreamCodec<ByteBuf, PlayerOrbitStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeDouble(p.vx());
                buf.writeDouble(p.vy());
                buf.writeDouble(p.vz());
                buf.writeInt(p.seq());
                buf.writeBoolean(p.reset());
            },
            buf -> new PlayerOrbitStatePayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
