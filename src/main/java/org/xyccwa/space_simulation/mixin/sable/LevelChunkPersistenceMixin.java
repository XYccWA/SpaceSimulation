package org.xyccwa.space_simulation.mixin.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.asteroid.entity.AsteroidEditTracker;

/**
 * 小行星方块改动检测（覆盖全部写入路径）。
 *
 * <p>{@code LevelChunk#setBlockState} 是服务端所有方块写入的汇聚点：玩家挖掘/放置、爆炸、活塞、
 * 流体、其它模组的 {@code level.setBlock}/{@code setBlockAndUpdate} 最终都走到这里
 * （sable 自己也在同一方法上挂钩子，把写入同步进 plot）。我们在 RETURN 处只问一句
 * "这个 chunk 属于某颗正在我们活动表里的小行星吗"，是则把它标记为已改动。
 *
 * <p>不误报的两个理由（见 {@link AsteroidEditTracker#onChunkBlockChanged}）：
 * 实体化装配阶段的落块发生在子层级入表之前；反序列化恢复走 {@code plot.load()} 直接填 section，
 * 不经过 setBlockState。
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkPersistenceMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void spaceSim$trackAsteroidEdit(BlockPos pos, BlockState state, boolean isMoving,
                                            CallbackInfoReturnable<BlockState> cir) {
        try {
            AsteroidEditTracker.onChunkBlockChanged((LevelChunk) (Object) this, pos);
        } catch (Throwable ignored) {
            // 检测失败绝不影响方块写入本身
        }
    }
}
