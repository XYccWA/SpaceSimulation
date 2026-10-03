package org.xyccwa.space_simulation.mixin.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.interaction.SubLevelInteractionLagCompensation;

/**
 * 子层级方块交互的延迟补偿入口。
 *
 * <p>{@code Player#canInteractWithBlock(BlockPos, double)} 是挖掘与放置**共用**的服务端距离校验
 * （{@code ServerPlayerGameMode#handleBlockBreakAction} 与
 * {@code ServerGamePacketListenerImpl#handleUseItemOn}）。sable 已经注入过它来处理子层级，
 * 但用的是服务端当刻位置；这里在它之前（priority 1500 > sable 的默认 1000）先做一次
 * "客户端时间轴"的判定，通过就直接放行。
 *
 * <p>补偿**只放行、从不拒绝**：{@link SubLevelInteractionLagCompensation#allow} 返回 false 时
 * 本注入不做任何事，交互继续走 sable 与原版的判定，最坏情况等价于没有补偿。
 */
@Mixin(value = Player.class, priority = 1500)
public abstract class PlayerSubLevelInteractionMixin {

    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void spaceSim$subLevelLagCompensation(BlockPos pos, double slop, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (SubLevelInteractionLagCompensation.allow((Player) (Object) this, pos, slop)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
            // 补偿失败即退回原判定，绝不影响正常交互
        }
    }
}
