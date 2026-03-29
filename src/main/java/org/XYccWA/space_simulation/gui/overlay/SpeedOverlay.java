package org.XYccWA.space_simulation.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class SpeedOverlay {
    private static float lastSpeed = -1;

    public static final IGuiOverlay HUD_SPEED = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        // 获取玩家当前速度
        Vec3 currentVelocity = player.getDeltaMovement();
        double totalVelocity = currentVelocity.length() * 20; // 转换为格/秒

        // 检查速度值是否发生变化
        if (Math.abs(totalVelocity - lastSpeed) > 0.01) {
            lastSpeed = (float) totalVelocity;
        }

        // 设置显示位置到屏幕右上角
        int speed_x = screenWidth - 60; // 距离屏幕右侧60像素
        int speed_y = 30; // 距离屏幕顶部30像素
        int speed_color = 0x00FFFF; // 青色

        Font font = gui.getFont();

        // 显示速度标签
        guiGraphics.drawString(font, Component.translatable("speed:"),
                speed_x - 60, speed_y, speed_color);

        // 显示速度值
        String speedText = String.format("%.2f 格/秒", lastSpeed);
        guiGraphics.drawString(font, Component.literal(speedText),
                speed_x, speed_y, speed_color);
    };
}
