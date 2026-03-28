// src/main/java/org/XYccWA/space_simulation/gui/overlay/FuelOverlay.java
package org.XYccWA.space_simulation.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.XYccWA.space_simulation.capability.CapabilityHandler;

public class FuelOverlay {
    public static final IGuiOverlay HUD_FUEL = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        // 获取燃料能力
        player.getCapability(CapabilityHandler.FUEL_REMAINING).ifPresent(fuel -> {
            // 计算燃料余量与最大值的百分比
            float fuelPercentage = (fuel.getFuelRemaining() / fuel.getMaxFuel()) * 100;

            // 设置燃料显示位置到屏幕右上角
            int fuel_x = screenWidth - 60; // 距离屏幕右侧60像素
            int fuel_y = 10; // 距离屏幕顶部10像素
            int fuel_color = 0xFF00FF; // 紫色

            var font = gui.getFont();

            // 显示燃料标签
            guiGraphics.drawString(font, Component.translatable("fuel_remaining"),
                    fuel_x - 50, fuel_y, fuel_color);

            // 显示燃料余量与最大值的比值，格式为 "当前值/100"
            String fuelText = String.format("%.1f/100", fuelPercentage);
            guiGraphics.drawString(font, Component.literal(fuelText),
                    fuel_x, fuel_y + 10, fuel_color);
        });
    };
}
