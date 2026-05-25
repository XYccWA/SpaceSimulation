package org.xyccwa.space_simulation.mixin.server.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(FlowingFluid.class)
public class FluidMixin {

    @Inject(method = "getFlow", at = @At("HEAD"), cancellable = true)
    private void onGetFlow(BlockGetter p_75987_, BlockPos p_75988_, FluidState p_75989_, CallbackInfoReturnable<Vec3> cir) {
        // 阻止流体水平流动 - 返回零向量表示无流动
        cir.setReturnValue(Vec3.ZERO);
    }

    @Inject(method = "getSpread", at = @At("HEAD"), cancellable = true)
    private void onGetSpread(Level p_256191_, BlockPos p_76081_, BlockState p_76082_, CallbackInfoReturnable<Map<Direction, FluidState>> cir) {
        // 阻止流体扩散 - 返回空Map表示无扩散
        cir.setReturnValue(Map.of());
    }

    @Inject(method = "getNewLiquid", at = @At("HEAD"), cancellable = true)
    private void onGetNewLiquid(Level p_256464_, BlockPos p_76037_, BlockState p_76038_, CallbackInfoReturnable<FluidState> cir) {
        // 阻止流体向下流动 - 返回当前流体状态
        cir.setReturnValue(p_76038_.getFluidState());
    }

    @Inject(method = "canSpreadTo", at = @At("HEAD"), cancellable = true)
    private void onCanSpreadTo(BlockGetter p_75978_, BlockPos p_75979_, BlockState p_75980_, Direction p_75981_, BlockPos p_75982_, BlockState p_75983_, FluidState p_75984_, Fluid p_75985_, CallbackInfoReturnable<Boolean> cir) {
        // 阻止流体向任何方向扩散
        cir.setReturnValue(false);
    }
}
