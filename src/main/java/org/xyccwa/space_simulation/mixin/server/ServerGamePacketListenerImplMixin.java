package org.xyccwa.space_simulation.mixin.server;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.interaction.InteractionTimeline;
import org.xyccwa.space_simulation.orbital.PlayerOrbitServer;

/**
 * 服务器权威移动：轨道力学开启时，忽略客户端的位置包，并把服务器自己积分出的位置写回去。
 *
 * 背景一（为什么取消 handleMovePlayer）：原版玩家移动是客户端权威——{@code handleMovePlayer}
 * 直接接受客户端报来的坐标（只做"移动过快/穿墙"这类启发式校验）。轨道力学要用真引力场积分，
 * 位置必须由服务器算，因此整体取消；服务器积分在 {@code player.doTick()} → travel 内完成
 * （见 PlayerOrbitServer.tick）。被取消后需要自行补上的原版副作用：区块跟踪（ChunkMap.move）
 * 与 setKnownMovement，都在 PlayerOrbitServer.tick 里做了。
 *
 * 背景二（为什么还要在 tick 末尾回写位置）：原版 {@code tick()} 的顺序是
 *   resetPosition() → player.doTick() → player.absMoveTo(firstGoodX/Y/Z)
 * ——最后那步会把服务器实体位置写回 tick 之前的值，只等客户端移动包来"确认"位移。
 * 我们又不接受客户端的移动包，于是每 tick 积分出的位移会被原版抹掉（玩家位置钉死、原地抖动）。
 * 因此必须在连接 tick 结束时调用 {@link PlayerOrbitServer#reassertAuthoritative} 写回权威位置。
 *
 * 三种情况不取消 handleMovePlayer，留给原版：
 *   - tickCount == 0：连接建立后的首次位置同步；
 *   - awaitingPositionFromClient != null：传送还没等到客户端确认（否则会每 20 tick 重传一次）；
 *   - 玩家在载具里：载具移动仍由原版处理。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Shadow
    @Nullable
    private Vec3 awaitingPositionFromClient;

    @Shadow
    private int tickCount;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void spaceSim$serverAuthoritativeMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // 子层级交互的延迟补偿要用"客户端所见位置"来对齐时间轴（见 InteractionTimeline）。
        // 这里只缓存它，绝不拿它做位移 —— 位移仍然完全由服务器积分。
        InteractionTimeline.recordClientPosition(this.player, packet);
        if (this.tickCount == 0) return;
        if (this.awaitingPositionFromClient != null) return;
        if (this.player.isPassenger()) return;
        if (!PlayerOrbitServer.isActive(this.player)) return;
        this.player.resetLastActionTime();
        ci.cancel();
    }

    /**
     * 原版 tick() 末尾的 absMoveTo(firstGood...) 会抹掉服务器自己算出的位移，
     * 这里在它之后把权威位置写回去（顺序由注入点 TAIL 保证）。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void spaceSim$reassertAuthoritative(CallbackInfo ci) {
        PlayerOrbitServer.reassertAuthoritative(this.player);
    }
}
