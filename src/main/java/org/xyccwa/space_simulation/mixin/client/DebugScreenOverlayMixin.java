package org.xyccwa.space_simulation.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.client.PlayerOrbitClient;
import org.xyccwa.space_simulation.orbital.Gravity;
import org.xyccwa.space_simulation.orbital.TwoBody;
import org.xyccwa.space_simulation.player.PlayerAcceleration;

import java.util.List;

@OnlyIn(Dist.CLIENT)
@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void addMovementInfo(CallbackInfoReturnable<List<String>> cir) {
        List<String> info = cir.getReturnValue();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player != null) {
            // 获取合速度和合加速度
            double speed = PlayerAcceleration.getCurrentVelocity(player).length() * 20;
            double acceleration = PlayerAcceleration.getCurrentAcceleration(player).length() * 400;

            // 查找XYZ坐标行的位置
            int insertIndex = -1;
            for (int i = 0; i < info.size(); i++) {
                String line = info.get(i);
                // 查找包含XYZ的行
                if (line.contains("XYZ:") && !line.contains("Chunk:")) {
                    insertIndex = i + 1;
                    break;
                }
            }

            // 如果没有找到XYZ行，就在最后添加
            if (insertIndex == -1) {
                insertIndex = info.size();
            }

            // 添加速度信息
            info.add(insertIndex++, "");
            info.add(insertIndex++, "§6=== Space Simulation ===");
            info.add(insertIndex++, String.format("§eSpeed: §f%.2f m/s", speed));
            info.add(insertIndex++, String.format("§eAcceleration: §f%.2f m/s²", acceleration));

            // 轨道力学：μ、半径、轨道根数与预测修正次数
            double mu = Gravity.mu();
            if (mu > 0.0) {
                double x = player.getX(), y = player.getY(), z = player.getZ();
                double vx = player.getDeltaMovement().x, vy = player.getDeltaMovement().y, vz = player.getDeltaMovement().z;
                double radius = Math.sqrt(x * x + y * y + z * z);
                double vspeed = Math.sqrt(vx * vx + vy * vy + vz * vz);
                info.add(insertIndex++, String.format("§eOrbit: §fμ=%.3e  r=%.0f  |v|=%.4f (v_circ=%.4f)",
                        mu, radius, vspeed, Math.sqrt(mu / Math.max(1.0, radius))));
                TwoBody.Elements el = TwoBody.fromState(
                        new double[]{x, y, z, }, new double[]{vx, vy, vz}, mu);
                if (el.valid) {
                    if (el.hyperbolic) {
                        info.add(insertIndex++, String.format("§eEllipse: §fhyperbolic  a=%.0f  e=%.4f  i=%.2f°",
                                el.a, el.e, Math.toDegrees(el.inclination)));
                    } else {
                        info.add(insertIndex++, String.format("§eOrbit: §fa=%.0f  e=%.4f  i=%.2f°  T=%.0f tick",
                                el.a, el.e, Math.toDegrees(el.inclination), el.periodTicks(mu)));
                    }
                }
                info.add(insertIndex, String.format("§eServer corrections: §f%d (offset %+d, last %.2f m)",
                        PlayerOrbitClient.corrections(), PlayerOrbitClient.lastMatchOffset(),
                        PlayerOrbitClient.lastCorrectionError()));
            }
        }
    }
}