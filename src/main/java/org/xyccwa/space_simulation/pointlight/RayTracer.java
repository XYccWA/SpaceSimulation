package org.xyccwa.space_simulation.pointlight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 射线检测器
 * 使用 Bresenham 3D 算法遍历射线经过的方块
 */
public class RayTracer {

    /**
     * 判断从 start 到 end 的直线上是否有遮挡
     * @return true=被遮挡，false=通畅
     */
    public boolean isOccluded(Level level, BlockPos start, BlockPos end) {
        // 遍历射线路径上的所有方块
        for (BlockPos pos : getLineBlocks(start, end)) {
            // 跳过起点和终点
            if (pos.equals(start) || pos.equals(end)) {
                continue;
            }

            // 检查该位置的方块是否不透明
            BlockState state = level.getBlockState(pos);
            if (isSolidOccluder(state)) {
                return true;  // 被遮挡
            }

            // TODO: 航空学物理结构检查
            // if (AeronauticsIntegration.isPositionOccupied(level, pos)) {
            //     return true;
            // }
        }
        return false;
    }

    /**
     * 判断方块是否为有效的遮挡物
     */
    private boolean isSolidOccluder(BlockState state) {
        // 通常不透明且碰撞箱完整的方块会遮挡光线
        return state.isSolid() && state.canOcclude();
    }

    /**
     * 3D Bresenham 算法生成射线经过的所有方块坐标
     * 按从起点到终点的顺序返回
     */
    private Iterable<BlockPos> getLineBlocks(BlockPos start, BlockPos end) {
        return () -> new Bresenham3DIterator(start, end);
    }

    /**
     * 3D Bresenham 迭代器实现
     */
    private static class Bresenham3DIterator implements java.util.Iterator<BlockPos> {
        private final int x1, y1, z1;
        private final int x2, y2, z2;
        private int x, y, z;
        private int dx, dy, dz;
        private int sx, sy, sz;
        private int err1, err2;
        private boolean done;

        public Bresenham3DIterator(BlockPos start, BlockPos end) {
            this.x1 = start.getX();
            this.y1 = start.getY();
            this.z1 = start.getZ();
            this.x2 = end.getX();
            this.y2 = end.getY();
            this.z2 = end.getZ();

            this.x = x1;
            this.y = y1;
            this.z = z1;

            this.dx = Math.abs(x2 - x1);
            this.dy = Math.abs(y2 - y1);
            this.dz = Math.abs(z2 - z1);

            this.sx = x1 < x2 ? 1 : -1;
            this.sy = y1 < y2 ? 1 : -1;
            this.sz = z1 < z2 ? 1 : -1;

            this.err1 = dx - dy;
            this.err2 = dx - dz;
            this.done = false;
        }

        @Override
        public boolean hasNext() {
            return !done;
        }

        @Override
        public BlockPos next() {
            if (done) {
                throw new java.util.NoSuchElementException();
            }

            BlockPos current = new BlockPos(x, y, z);

            // 到达终点
            if (x == x2 && y == y2 && z == z2) {
                done = true;
                return current;
            }

            // Bresenham 步进算法
            int e2 = 2 * err1;
            int e3 = 2 * err2;

            if (e2 > -dy) {
                err1 -= dy;
                err2 -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err1 += dx;
                // y 方向步进
            }
            if (e3 > -dz) {
                err2 -= dz;
                // z 方向步进
            }
            if (e3 < dx) {
                err2 += dx;
                // 完整步进需要同时处理
            }

            // 简化版：每次步进一个轴向（更简单但足够精确）
            // 这里使用更直接的实现
            step();

            return current;
        }

        private void step() {
            // 重新实现一个更清晰的步进逻辑
            int x = this.x, y = this.y, z = this.z;
            int dx = this.dx, dy = this.dy, dz = this.dz;
            int sx = this.sx, sy = this.sy, sz = this.sz;

            int err1 = this.err1, err2 = this.err2;

            if (x != x2 || y != y2 || z != z2) {
                int e1 = 2 * err1;
                int e2 = 2 * err2;

                if (e1 > -dy) {
                    err1 -= dy;
                    err2 -= dz;
                    x += sx;
                }
                if (e1 < dx) {
                    err1 += dx;
                    // y 变化
                    int e3 = 2 * err2;
                    if (e3 > -dz) {
                        err2 -= dz;
                        y += sy;
                    }
                    if (e3 < dx) {
                        err2 += dx;
                        z += sz;
                    }
                }
            }

            this.x = x;
            this.y = y;
            this.z = z;
            this.err1 = err1;
            this.err2 = err2;
        }
    }
}