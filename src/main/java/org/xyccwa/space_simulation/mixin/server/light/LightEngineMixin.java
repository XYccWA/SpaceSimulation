package org.xyccwa.space_simulation.mixin.server.light;

import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(LightEngine.class)
public class LightEngineMixin {

    private static final Set<LightEngine> INITIALIZED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject(method = "runLightUpdates", at = @At("HEAD"), cancellable = true)
    private void onRunLightUpdates(CallbackInfoReturnable<Integer> cir) {
        if (!SpaceSimulationConfig.enableLightShortCircuit.get()) return;

        LightEngine self = (LightEngine)(Object)this;

        // 首次调用放行
        if (!INITIALIZED.contains(self)) {
            INITIALIZED.add(self);
            return;
        }

        cir.setReturnValue(0);
    }
}
