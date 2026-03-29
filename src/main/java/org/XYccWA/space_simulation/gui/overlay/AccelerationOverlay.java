package org.XYccWA.space_simulation.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.XYccWA.space_simulation.player.PlayerPositionTracker;

public class AccelerationOverlay {
    private static float lastAcceleration = -1;

    public static final IGuiOverlay HUD_ACCELERATION = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        // 获取玩家当前加速度
        double currentAcceleration = PlayerPositionTracker.getPlayerAcceleration(player);

        // 检查加速度值是否发生变化
        if (Math.abs(currentAcceleration - lastAcceleration) > 0.01) {
            lastAcceleration = (float) currentAcceleration;
        }

        // 设置显示位置到屏幕右上角
        int accel_x = screenWidth - 60; // 距离屏幕右侧60像素
        int accel_y = 50; // 距离屏幕顶部50像素
        int accel_color = 0xFFFF00; // 黄色

        Font font = gui.getFont();

        // 显示加速度标签
        guiGraphics.drawString(font, Component.translatable("acceleration:"),
                accel_x - 80, accel_y, accel_color);

        // 显示加速度值
        String accelText = String.format("%.2f 格/秒²", lastAcceleration);
        guiGraphics.drawString(font, Component.literal(accelText),
                accel_x, accel_y, accel_color);
    };
}
