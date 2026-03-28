// src/main/java/org/XYccWA/space_simulation/command/FuelCommand.java
package org.XYccWA.space_simulation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.XYccWA.space_simulation.capability.CapabilityHandler;

public class FuelCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("fuel")
                        .executes(FuelCommand::getFuelRemaining)
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0))
                                        .requires(source -> source.hasPermission(2)) // 需要管理员权限
                                        .executes(FuelCommand::setFuelRemaining)
                                )
                        )
                        .then(Commands.literal("setmax")
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(1))
                                        .requires(source -> source.hasPermission(2)) // 需要管理员权限
                                        .executes(FuelCommand::setMaxFuel)
                                )
                        )
        );
    }

    // 获取燃料余量的方法（已存在）
    private static int getFuelRemaining(CommandContext<CommandSourceStack> context) {
        try {
            Player player = context.getSource().getPlayerOrException();
            player.getCapability(CapabilityHandler.FUEL_REMAINING).ifPresent(fuel -> {
                float remaining = fuel.getFuelRemaining();
                float maxFuel = fuel.getMaxFuel();
                player.sendSystemMessage(Component.literal(String.format(
                        "当前燃料余量: %.2f / %.2f", remaining, maxFuel
                )));
            });
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("无法获取玩家燃料数据"));
            return 0;
        }
    }

    // 设置燃料余量的新方法
    private static int setFuelRemaining(CommandContext<CommandSourceStack> context) {
        try {
            Player player = context.getSource().getPlayerOrException();
            float amount = FloatArgumentType.getFloat(context, "amount");

            player.getCapability(CapabilityHandler.FUEL_REMAINING).ifPresent(fuel -> {
                fuel.setFuelRemaining(amount);
                float remaining = fuel.getFuelRemaining();
                float maxFuel = fuel.getMaxFuel();
                player.sendSystemMessage(Component.literal(String.format(
                        "燃料余量已设置为: %.2f / %.2f", remaining, maxFuel
                )));
            });
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("无法设置燃料余量"));
            return 0;
        }
    }

    // 设置最大燃料值的方法（已存在）
    private static int setMaxFuel(CommandContext<CommandSourceStack> context) {
        try {
            Player player = context.getSource().getPlayerOrException();
            float amount = FloatArgumentType.getFloat(context, "amount");

            player.getCapability(CapabilityHandler.FUEL_REMAINING).ifPresent(fuel -> {
                fuel.setMaxFuel(amount);
                player.sendSystemMessage(Component.literal(String.format(
                        "最大燃料值已设置为: %.2f", amount
                )));
            });
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("无法设置最大燃料值"));
            return 0;
        }
    }
}
