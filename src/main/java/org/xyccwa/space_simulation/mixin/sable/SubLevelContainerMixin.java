package org.xyccwa.space_simulation.mixin.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.sable.PlotYardPlacement;
import org.xyccwa.space_simulation.sable.PlotYardPlacement.Placement;

/**
 * 把 sable plotyard（子层级实际方块存储区）从默认的 20,480,000 块处移入太阳（世界原点球体）内部。
 *
 * 注入点：{@link SubLevelContainer} 构造器 TAIL（super() 之后、子类构造器体之前），
 * 直接覆盖 {@code logSideLength}/{@code originX}/{@code originZ} 三个 final 字段，使：
 * - 网格紧贴世界原点旁（origin=1 plot，(0,0,0) 不在网格内），整个网格（最远角点 49,238 块
 *   ≤ 50,000 = 世界最小太阳半径）完全位于太阳内；
 * - 坐标量级从 2048 万块降到 ≤ 34,816 块，rapier f32 的 ULP 从 2 块降到 ≈ 0.0039 块；
 * - 子层级逻辑位置/玩家交互/存档不受影响（logicalPose 编码映射与 plotyard 绝对位置解耦）。
 *
 * 说明：
 * - {@code subLevels} 数组按构造参数分配（偏大无害：逻辑索引按字段 logSideLength 计算，不越界）；
 * - 网格尺寸由 PlotYardPlacement 保证不超过世界最小太阳半径，因此任何世界都不会越出太阳面；
 *   仅当太阳最小半径本身容不下最小网格时保持原参数（applied=false），维持 sable 默认行为；
 * - 仅当 sable 加载时本 mixin 生效（sable 未安装时目标类不存在，mixin 整体跳过）。
 */
@Mixin(SubLevelContainer.class)
public abstract class SubLevelContainerMixin {

    @Shadow
    @Final
    @Mutable
    private int logSideLength;

    @Shadow
    @Final
    @Mutable
    private int originX;

    @Shadow
    @Final
    @Mutable
    private int originZ;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void spaceSim$placePlotYardInsideSun(final CallbackInfo ci) {
        final Placement placement = PlotYardPlacement.current();
        if (!placement.applied()) {
            return;
        }

        this.logSideLength = placement.logSideLength();
        this.originX = placement.originX();
        this.originZ = placement.originZ();
    }
}
