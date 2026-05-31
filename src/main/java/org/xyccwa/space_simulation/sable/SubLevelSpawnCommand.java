package org.xyccwa.space_simulation.sable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Set;

@EventBusSubscriber
public class SubLevelSpawnCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("sublevelspawn")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("X", IntegerArgumentType.integer())
                                .then(Commands.argument("Y", IntegerArgumentType.integer())
                                        .then(Commands.argument("Z", IntegerArgumentType.integer())
                                                .executes(SubLevelSpawnCommand::execute)
                                        )
                                )
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int x = IntegerArgumentType.getInteger(context, "X");
        int y = IntegerArgumentType.getInteger(context, "Y");
        int z = IntegerArgumentType.getInteger(context, "Z");

        BlockPos anchor = new BlockPos(x, y, z);
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        // 检查锚点位置是否有方块
        if (level.getBlockState(anchor).isAir()) {
            source.sendFailure(Component.literal("§c锚点位置 (" + x + ", " + y + ", " + z + ") 没有方块！"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§e正在收集方块..."), false);

        // 收集相连的方块
        SubLevelAssemblyHelper.GatherResult result = SubLevelAssemblyHelper.gatherConnectedBlocks(
                anchor, level, 100000, null
        );

        // 检查收集结果
        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            switch (result.assemblyState()) {
                case NO_BLOCKS:
                    source.sendFailure(Component.literal("§c没有找到任何相连的方块！"));
                    break;
                case TOO_MANY_BLOCKS:
                    source.sendFailure(Component.literal("§c方块数量超过限制（最大 100000 个）！"));
                    break;
                default:
                    source.sendFailure(Component.literal("§c收集方块失败：" + result.assemblyState().errorKey));
                    break;
            }
            return 0;
        }

        Set<BlockPos> blocks = result.blocks();
        BoundingBox3i bounds = result.boundingBox();

        if (blocks == null || blocks.isEmpty()) {
            source.sendFailure(Component.literal("§c没有收集到任何方块！"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§e找到 §6" + blocks.size() + " §e个方块，正在创建子层级..."), false);

        // 获取子层级容器
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            source.sendFailure(Component.literal("§c无法获取子层级容器！"));
            return 0;
        }

        try {
            // 创建子层级
            ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);

            if (subLevel != null) {
                // 成功消息
                source.sendSuccess(() -> Component.literal(
                        "§a✓ 子层级创建成功！\n" +
                                "§7UUID: §f" + subLevel.getUniqueId() + "\n" +
                                "§7方块数量: §f" + blocks.size() + "\n" +
                                "§7位置: §f(" + x + ", " + y + ", " + z + ")"
                ), true);

                return 1;
            } else {
                source.sendFailure(Component.literal("§c子层级创建失败！"));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c创建子层级时发生错误: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}