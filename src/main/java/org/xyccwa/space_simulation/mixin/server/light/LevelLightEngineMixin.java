package org.xyccwa.space_simulation.mixin.server.light;

import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(LevelLightEngine.class)
public class LevelLightEngineMixin {

    private static final Set<LevelLightEngine> INITIALIZED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject(method = "runLightUpdates", at = @At("HEAD"), cancellable = true)
    private void onRunLightUpdates(CallbackInfoReturnable<Integer> cir) {
        if (!SpaceSimulationConfig.enableLightShortCircuit.get()) return;

        LevelLightEngine self = (LevelLightEngine)(Object)this;

        // 首次调用时放行，让光照引擎完成初始化
        if (!INITIALIZED.contains(self)) {
            INITIALIZED.add(self);
            return;  // 执行原版逻辑
        }

        // 初始化完成后，阻断所有后续更新
        cir.setReturnValue(0);
    }
}
