package org.xyccwa.space_simulation.mixin.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    // 原有常量
    private static final float RADIAN_CONVERTER = (float) (Math.PI / 180.0);
    private static final float DAMPING_FACTOR = 1.0F;
    private static final long GEAR_COOLDOWN_MS = 200;
    private static final int MIN_GEAR = 1;
    private static final int MAX_GEAR = 5;
    private static final float BASE_ACCELERATION = 5.0F;
    private static final float ACCELERATION_INCREMENT = 5.0F;

    private static final Map<Player, Integer> gearLevelMap = new WeakHashMap<>();
    private static final Map<Player, Long> lastGearChangeTime = new WeakHashMap<>();
    private static final Map<Player, MovementMode> movementModeMap = new WeakHashMap<>();

    // 燃料消耗相关常量
    private static final float BASE_FUEL_CONSUMPTION = 0.01f; // 1档每秒消耗0.01
    private static final float FUEL_CONSUMPTION_INCREMENT = 0.01f; // 每档增加0.01

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.typeHolder().is(DamageTypes.FELL_OUT_OF_WORLD) || source.typeHolder().is(DamageTypes.FALL)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isFallFlying", at = @At("HEAD"), cancellable = true)
    private void alwaysFallFlying(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void onJumpFromGround(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            ci.cancel(); // 取消玩家的跳跃
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravel(Vec3 travelVector, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            Player player = (Player) entity;
//            ci.cancel();
        }
    }
}

