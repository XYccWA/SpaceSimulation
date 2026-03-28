package org.XYccWA.space_simulation.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.XYccWA.space_simulation.player.MovementMode;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // 检查伤害源是否为虚空伤害
        if (source.typeHolder().is(DamageTypes.FELL_OUT_OF_WORLD)||source.typeHolder().is(DamageTypes.FALL)) {
            // 取消伤害事件
            cir.setReturnValue(false);
        }
    }

    private static final float RADIAN_CONVERTER = (float) (Math.PI / 180.0);
    private static final float DAMPING_FACTOR = 1.0F; // 阻尼系数
    private static final Map<Player, Boolean> rKeyPressedMap = new HashMap<>(); // 追踪R键按下状态

    private static final Map<Player, Integer> gearLevelMap = new HashMap<>(); // 档位映射
    private static final Map<Player, Long> lastGearChangeTime = new HashMap<>(); // 档位切换冷却
    private static final long GEAR_COOLDOWN_MS = 200; // 档位切换冷却时间

    // 档位配置
    private static final int MIN_GEAR = 1;
    private static final int MAX_GEAR = 5;
    private static final float BASE_ACCELERATION = 5.0F; // 1档加速度（格/秒²）
    private static final float ACCELERATION_INCREMENT = 5.0F; // 每档增加的加速度

    private static final Map<Player, MovementMode> movementModeMap = new HashMap<>();

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravel(Vec3 travelVector, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            Player player = (Player) entity;

            if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_R)) {
                // 检查R键是否之前未按下
                if (!rKeyPressedMap.getOrDefault(player, false)) {
                    // 切换模式
                    MovementMode currentMode = movementModeMap.getOrDefault(player, MovementMode.FREE_MOVEMENT);
                    movementModeMap.put(player, currentMode == MovementMode.FREE_MOVEMENT ?
                            MovementMode.HOVER : MovementMode.FREE_MOVEMENT);
                    player.sendSystemMessage(Component.literal("切换到: " +
                            (currentMode == MovementMode.FREE_MOVEMENT ? "悬停模式" : "自由运动模式")));
                    // 标记R键为按下状态
                    rKeyPressedMap.put(player, true);
                }
            } else {
                // R键松开，重置状态
                rKeyPressedMap.put(player, false);
            }

            // 根据当前模式执行不同逻辑
            MovementMode mode = movementModeMap.getOrDefault(player, MovementMode.FREE_MOVEMENT);

            if (mode == MovementMode.HOVER) {
                handleHoverMode(player);
            } else {
                handleFreeMovement(player, travelVector);
            }
            // 档位切换逻辑
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                    InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                changeGear(player, 1); // Shift升档
            } else if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
                    InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                changeGear(player, -1); // Ctrl降档
            }
            ci.cancel();
        }
    }


    private void handleFreeMovement(Player player, Vec3 travelVector) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;

            float forward = player.zza;
            float strafe = player.xxa;
            float yaw = player.getYRot();
            float pitch = player.getXRot();

            double yawRad = yaw * RADIAN_CONVERTER;
            double pitchRad = pitch * RADIAN_CONVERTER;

            Vec3 currentVelocity = entity.getDeltaMovement();
            Vec3 newVelocity;

            // 获取当前档位和加速度值
            int gear = gearLevelMap.getOrDefault(player, 1);
            float accelerationValue = (BASE_ACCELERATION + (gear - 1) * ACCELERATION_INCREMENT) / 400.0F;

            // 检测垂直运动按键
            long window = Minecraft.getInstance().getWindow().getWindow();
            boolean spacePressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
            boolean cPressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_C);

            // 计算垂直于视线的方向向量
            double verticalX = -Math.sin(yawRad) * Math.sin(pitchRad);
            double verticalY = Math.cos(pitchRad);
            double verticalZ = Math.cos(yawRad) * Math.sin(pitchRad);

            Vec3 verticalDirection = new Vec3(verticalX, verticalY, verticalZ).normalize();

            if (forward == 0 && strafe == 0 && !spacePressed && !cPressed) {
                // 无输入时应用阻尼
                newVelocity = currentVelocity.scale(DAMPING_FACTOR);
            } else {
                Vec3 acceleration = Vec3.ZERO;

                // 水平运动加速度
                if (forward != 0 || strafe != 0) {
                    double x = -Math.sin(yawRad) * Math.cos(pitchRad) * forward + Math.cos(yawRad) * strafe;
                    double y = -Math.sin(pitchRad) * forward;
                    double z = Math.cos(yawRad) * Math.cos(pitchRad) * forward + Math.sin(yawRad) * strafe;
                    acceleration = acceleration.add(new Vec3(x, y, z).normalize().scale(accelerationValue));
                }

                // 垂直运动加速度
                if (spacePressed) {
                    acceleration = acceleration.add(verticalDirection.scale(accelerationValue));
                } else if (cPressed) {
                    acceleration = acceleration.add(verticalDirection.scale(-accelerationValue));
                }

                newVelocity = currentVelocity.add(acceleration);
            }

            // 应用新速度
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

        // 检测玩家是否有运动输入
        float forward = player.zza;
        float strafe = player.xxa;

        // 检测垂直运动按键
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean spacePressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
        boolean cPressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_C);

        boolean hasInput = forward != 0 || strafe != 0 || spacePressed || cPressed;

        if (hasInput) {
            // 有任何输入时执行自由运动逻辑
            handleFreeMovement(player, Vec3.ZERO);
        } else {
            // 获取当前档位和加速度值
            int gear = gearLevelMap.getOrDefault(player, 1);
            float accelerationValue = (BASE_ACCELERATION + (gear - 1) * ACCELERATION_INCREMENT) / 400.0F;

            // 计算刹车方向（与速度方向相反）
            Vec3 brakeDirection = currentVelocity.normalize().scale(-1);

            // 应用与当前档位相等的刹车加速度
            Vec3 brakeAcceleration = brakeDirection.scale(accelerationValue);
            Vec3 newVelocity = currentVelocity.add(brakeAcceleration);

            // 防止速度反向（当速度很小时直接停止）
            if (newVelocity.dot(currentVelocity) < 0) {
                newVelocity = Vec3.ZERO;
            }

            player.setDeltaMovement(newVelocity);
            player.move(MoverType.SELF, newVelocity);
        }
    }



    private void changeGear(Player player, int delta) {
        long currentTime = System.currentTimeMillis();
        Long lastChangeTime = lastGearChangeTime.get(player);

        // 检查冷却时间
        if (lastChangeTime != null && currentTime - lastChangeTime < GEAR_COOLDOWN_MS) {
            return;
        }

        // 获取当前档位
        int currentGear = gearLevelMap.getOrDefault(player, 1);
        int newGear = currentGear + delta;

        // 限制档位范围
        newGear = Math.max(MIN_GEAR, Math.min(MAX_GEAR, newGear));

        // 档位未变化则不处理
        if (newGear == currentGear) {
            return;
        }

        // 更新档位和冷却时间
        gearLevelMap.put(player, newGear);
        lastGearChangeTime.put(player, currentTime);

        // 计算当前加速度
        float acceleration = BASE_ACCELERATION + (newGear - 1) * ACCELERATION_INCREMENT;

        // 发送提示信息
        player.sendSystemMessage(Component.literal(String.format(
                "档位: %d | 加速度: %.1f 格/秒²", newGear, acceleration
        )));
    }
}

