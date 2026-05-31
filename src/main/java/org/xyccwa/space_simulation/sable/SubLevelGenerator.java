package org.xyccwa.space_simulation.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * 子层级生成器 - 将指定位置的方块组装成独立的移动子层级
 */
public class SubLevelGenerator {

    /**
     * 将指定位置的方块组装成子层级
     *
     * @param level      服务端世界
     * @param anchor     锚点位置（将成为子层级的中心点）
     * @param blocks     要组装的方块位置集合
     * @param bounds     边界框（用于移动实体和跟踪点）
     * @return 生成的子层级，失败返回 null
     */
    @Nullable
    public static ServerSubLevel assemble(
            ServerLevel level,
            BlockPos anchor,
            Set<BlockPos> blocks,
            BoundingBox3ic bounds
    ) {
        // 获取子层级容器
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            Sable.LOGGER.error("无法获取子层级容器，世界可能未初始化子层级系统");
            return null;
        }

        // 检查是否有方块需要组装
        if (blocks == null || blocks.isEmpty()) {
            Sable.LOGGER.error("没有提供任何方块，无法创建子层级");
            return null;
        }

        try {
            // 调用 Sable API 组装子层级
            return SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);
        } catch (Exception e) {
            Sable.LOGGER.error("创建子层级失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 自动收集并组装相连的方块
     *
     * @param level              服务端世界
     * @param anchor             锚点位置（将成为子层级的中心点）
     * @param maxBlocks          最大收集方块数量
     * @return 生成的子层级，失败返回 null
     */
    @Nullable
    public static ServerSubLevel assembleConnected(
            ServerLevel level,
            BlockPos anchor,
            int maxBlocks
    ) {
        // 获取子层级容器
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            Sable.LOGGER.error("无法获取子层级容器");
            return null;
        }

        // 收集相连的方块
        SubLevelAssemblyHelper.GatherResult result = SubLevelAssemblyHelper.gatherConnectedBlocks(
                anchor, level, maxBlocks, null
        );

        // 检查收集结果
        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            Sable.LOGGER.error("收集方块失败: {}", result.assemblyState().errorKey);
            return null;
        }

        Set<BlockPos> blocks = result.blocks();
        BoundingBox3i bounds = result.boundingBox();

        if (blocks == null || blocks.isEmpty()) {
            Sable.LOGGER.error("没有收集到任何方块");
            return null;
        }

        // 使用收集到的方块创建子层级
        return assemble(level, anchor, blocks, bounds);
    }

    /**
     * 简单版：将锚点所在的单个方块组建成子层级
     *
     * @param level  服务端世界
     * @param anchor 锚点位置
     * @return 生成的子层级，失败返回 null
     */
    @Nullable
    public static ServerSubLevel assembleSingleBlock(
            ServerLevel level,
            BlockPos anchor
    ) {
        Set<BlockPos> blocks = new HashSet<>();
        blocks.add(anchor);

        // 创建边界框（以锚点为中心，3x3x3 范围）
        BoundingBox3i bounds = new BoundingBox3i(
                anchor.getX() - 1, anchor.getY() - 1, anchor.getZ() - 1,
                anchor.getX() + 1, anchor.getY() + 1, anchor.getZ() + 1
        );

        return assemble(level, anchor, blocks, bounds);
    }
}