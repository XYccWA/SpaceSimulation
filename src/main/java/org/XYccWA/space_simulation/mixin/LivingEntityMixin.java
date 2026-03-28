package org.XYccWA.space_simulation.mixin;

import net.minecraft.network.chat.Component;
import org.XYccWA.space_simulation.client.KeyMappingHandler;
import org.XYccWA.space_simulation.capability.CapabilityHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.XYccWA.space_simulation.player.MovementMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    // 原有常量
    private static final float RADIAN_CONVERTER = (float) (Math.PI / 180.0);
    private static final float DAMPING_FACTOR = 1.0F;
    private static final Map<Player, Boolean> rKeyPressedMap = new HashMap<>();
    private static final Map<Player, Integer> gearLevelMap = new HashMap<>();
    private static final Map<Player, Long> lastGearChangeTime = new HashMap<>();
    private static final long GEAR_COOLDOWN_MS = 200;
    private static final int MIN_GEAR = 1;
    private static final int MAX_GEAR = 5;
    private static final float BASE_ACCELERATION = 5.0F;
    private static final float ACCELERATION_INCREMENT = 5.0F;
    private static final Map<Player, MovementMode> movementModeMap = new HashMap<>();

    // 燃料消耗相关常量
    private static final float BASE_FUEL_CONSUMPTION = 0.01f; // 1档每秒消耗0.01
    private static final float FUEL_CONSUMPTION_INCREMENT = 0.01f; // 每档增加0.01

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.typeHolder().is(DamageTypes.FELL_OUT_OF_WORLD) || source.typeHolder().is(DamageTypes.FALL)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravel(Vec3 travelVector, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            Player player = (Player) entity;

            // 使用KeyMapping检测R键
            if (KeyMappingHandler.TOGGLE_MODE_KEY.consumeClick()) {
                MovementMode currentMode = movementModeMap.getOrDefault(player, MovementMode.FREE_MOVEMENT);
                movementModeMap.put(player, currentMode == MovementMode.FREE_MOVEMENT ?
                        MovementMode.HOVER : MovementMode.FREE_MOVEMENT);
                player.sendSystemMessage(Component.literal("切换到: " +
                        (currentMode == MovementMode.FREE_MOVEMENT ? "悬停模式" : "自由运动模式")));
            }

            MovementMode mode = movementModeMap.getOrDefault(player, MovementMode.FREE_MOVEMENT);

            if (mode == MovementMode.HOVER) {
                handleHoverMode(player);
            } else {
                handleFreeMovement(player);
            }

            // 使用KeyMapping检测档位切换
            if (KeyMappingHandler.GEAR_UP_KEY.consumeClick()) {
                changeGear(player, 1);
            } else if (KeyMappingHandler.GEAR_DOWN_KEY.consumeClick()) {
                changeGear(player, -1);
            }

            ci.cancel();
        }
    }

    private void handleFreeMovement(Player player) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            // 取消重力
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;

            // 使用KeyMapping检测所有运动按键
            boolean wPressed = KeyMappingHandler.W_KEY.isDown();
            boolean sPressed = KeyMappingHandler.S_KEY.isDown();
            boolean aPressed = KeyMappingHandler.A_KEY.isDown();
            boolean dPressed = KeyMappingHandler.D_KEY.isDown();
            boolean spacePressed = KeyMappingHandler.SPACE_KEY.isDown();
            boolean cPressed = KeyMappingHandler.C_KEY.isDown();

            // 计算前进和侧向输入
            float forward = 0;
            float strafe = 0;

            if (wPressed) forward += 1;
            if (sPressed) forward -= 1;
            if (aPressed) strafe += 1;
            if (dPressed) strafe -= 1;

            float yaw = player.getYRot();
            float pitch = player.getXRot();

            double yawRad = yaw * RADIAN_CONVERTER;
            double pitchRad = pitch * RADIAN_CONVERTER;

            Vec3 currentVelocity = entity.getDeltaMovement();
            Vec3 newVelocity;

            int gear = gearLevelMap.getOrDefault(player, 1);
            float accelerationValue = (BASE_ACCELERATION + (gear - 1) * ACCELERATION_INCREMENT) / 400.0F;

            // 计算当前档位的燃料消耗率 (每秒)
            float fuelConsumptionRate = BASE_FUEL_CONSUMPTION + (gear - 1) * FUEL_CONSUMPTION_INCREMENT;
            // 转换为每tick消耗 (1秒=20tick)
            float fuelPerTick = fuelConsumptionRate / 20.0f;

            // 判断是否有输入
            boolean hasInput = forward != 0 || strafe != 0 || spacePressed || cPressed;

            double verticalX = -Math.sin(yawRad) * Math.sin(pitchRad);
            double verticalY = Math.cos(pitchRad);
            double verticalZ = Math.cos(yawRad) * Math.sin(pitchRad);

            Vec3 verticalDirection = new Vec3(verticalX, verticalY, verticalZ).normalize();

            // 检查燃料是否足够
            boolean hasFuel = player.getCapability(CapabilityHandler.FUEL_REMAINING).map(fuel -> {
                if (fuel.getFuelRemaining() > 0) {
                    return true;
                } else {
                    player.sendSystemMessage(Component.literal("燃料耗尽！请添加燃料"));
                    return false;
                }
            }).orElse(true); // 如果没有燃料能力，默认为有燃料

            if (forward == 0 && strafe == 0 && !spacePressed && !cPressed && hasFuel) {
                newVelocity = currentVelocity.scale(DAMPING_FACTOR);
            } else {
                Vec3 acceleration = Vec3.ZERO;

                if (forward != 0 || strafe != 0) {
                    double x = -Math.sin(yawRad) * Math.cos(pitchRad) * forward + Math.cos(yawRad) * strafe;
                    double y = -Math.sin(pitchRad) * forward;
                    double z = Math.cos(yawRad) * Math.cos(pitchRad) * forward + Math.sin(yawRad) * strafe;
                    acceleration = acceleration.add(new Vec3(x, y, z).normalize().scale(accelerationValue));
                }

                if (spacePressed) {
                    acceleration = acceleration.add(verticalDirection.scale(accelerationValue));
                } else if (cPressed) {
                    acceleration = acceleration.add(verticalDirection.scale(-accelerationValue));
                }

                newVelocity = currentVelocity.add(acceleration);
            }

            // 修改燃料消耗逻辑：只要有输入且有燃料就消耗燃料
            if (hasInput && hasFuel) {
                player.getCapability(CapabilityHandler.FUEL_REMAINING).ifPresent(fuel -> {
                    fuel.consumeFuel(fuelPerTick);
                });
            }

            if (newVelocity.lengthSqr() > 0.0001) {
                entity.setDeltaMovement(newVelocity);
                entity.move(MoverType.SELF, newVelocity);
            } else {
                entity.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private void handleHoverMode(Player player) {
        Vec3 currentVelocity = player.getDeltaMovement();

        // 使用KeyMapping检测所有运动按键
        boolean wPressed = KeyMappingHandler.W_KEY.isDown();
        boolean sPressed = KeyMappingHandler.S_KEY.isDown();
        boolean aPressed = KeyMappingHandler.A_KEY.isDown();
        boolean dPressed = KeyMappingHandler.D_KEY.isDown();
        boolean spacePressed = KeyMappingHandler.SPACE_KEY.isDown();
        boolean cPressed = KeyMappingHandler.C_KEY.isDown();

        // 计算前进和侧向输入
        float forward = 0;
        float strafe = 0;

        if (wPressed) forward += 1;
        if (sPressed) forward -= 1;
        if (aPressed) strafe += 1;
        if (dPressed) strafe -= 1;

        boolean hasInput = forward != 0 || strafe != 0 || spacePressed || cPressed;

        // 获取当前档位
        int gear = gearLevelMap.getOrDefault(player, 1);
        // 计算当前档位的燃料消耗率 (每秒)
        float fuelConsumptionRate = BASE_FUEL_CONSUMPTION + (gear - 1) * FUEL_CONSUMPTION_INCREMENT;
        // 转换为每tick消耗 (1秒=20tick)
        float fuelPerTick = fuelConsumptionRate / 20.0f;

        // 检查燃料是否足够
        boolean hasFuel = player.getCapability(CapabilityHandler.FUEL_REMAINING).map(fuel -> {
            if (fuel.getFuelRemaining() > 0) {
                return true;
            } else {
                player.sendSystemMessage(Component.literal("燃料耗尽！请添加燃料"));
                return false;
            }
        }).orElse(true); // 如果没有燃料能力，默认为有燃料

        if (hasInput) {
            // 有输入时，使用自由运动模式处理，燃料消耗率与自由运动模式相同
            handleFreeMovement(player);
        } else {
            float accelerationValue = (BASE_ACCELERATION + (gear - 1) * ACCELERATION_INCREMENT) / 400.0F;

            Vec3 brakeDirection = currentVelocity.normalize().scale(-1);
            Vec3 brakeAcceleration = brakeDirection.scale(accelerationValue);
            Vec3 newVelocity = currentVelocity.add(brakeAcceleration);

            if (newVelocity.dot(currentVelocity) < 0) {
                newVelocity = Vec3.ZERO;
            }

            player.setDeltaMovement(newVelocity);
            player.move(MoverType.SELF, newVelocity);

            // 悬停模式下减速时也按档位消耗燃料
            if (hasFuel && newVelocity.lengthSqr() > 0.0001) {
                player.getCapability(CapabilityHandler.FUEL_REMAINING).ifPresent(fuel -> {
                    fuel.consumeFuel(fuelPerTick);
                });
            }
        }
    }

    private void changeGear (Player player ,int delta){
        long currentTime = System.currentTimeMillis();
        Long lastChangeTime = lastGearChangeTime.get(player);

        if (lastChangeTime != null && currentTime - lastChangeTime < GEAR_COOLDOWN_MS) {
            return;
        }

        int currentGear = gearLevelMap.getOrDefault(player, 1);
        int newGear = currentGear + delta;

        newGear = Math.max(MIN_GEAR, Math.min(MAX_GEAR, newGear));

        if (newGear == currentGear) {
            return;
        }

        gearLevelMap.put(player, newGear);
        lastGearChangeTime.put(player, currentTime);

        float acceleration = BASE_ACCELERATION + (newGear - 1) * ACCELERATION_INCREMENT;
        float fuelConsumptionRate = BASE_FUEL_CONSUMPTION + (newGear - 1) * FUEL_CONSUMPTION_INCREMENT;

        player.sendSystemMessage(Component.literal(String.format(
                "档位: %d | 加速度: %.1f 格/秒² | 燃料消耗: %.1f 单位/秒",
                newGear, acceleration, fuelConsumptionRate
        )));
    }
}

