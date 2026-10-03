package org.xyccwa.space_simulation.asteroid.entity;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 小行星改动检测 —— 把"玩家在某颗小行星上动过方块"翻译成持久化台账里的改动标记。
 *
 * <h2>为什么要检测</h2>
 * sable 会把每颗活跃子层级都落盘，但我们的策略是"只有被玩家改动过的小行星才长期保留"
 * （见 {@link AsteroidPersistence}）：未改动的卸载时直接丢弃，内容与结构模板重建等价。
 * 于是需要一个"这颗被动过"的判据。
 *
 * <h2>两层检测</h2>
 * <ol>
 *   <li><b>主检测（覆盖全部写入路径）</b>：{@code LevelChunk#setBlockState} 的 mixin
 *       （{@code LevelChunkPersistenceMixin}）—— 玩家挖掘/放置、爆炸、活塞、流体、
 *       其它模组的 {@code setBlock}/{@code setBlockAndUpdate} 都汇聚到这里。</li>
 *   <li><b>兜底（NeoForge 事件）</b>：玩家挖掘 / 实体放置 / 爆炸三个事件。mixin 因版本变动
 *       失效时仍能保住最常见的改造途径。</li>
 * </ol>
 * 两层都只是往台账里写一个幂等的标记，重复命中无副作用。
 *
 * <h2>已知边界</h2>
 * 直接调 {@code LevelChunkSection#setBlockState(x,y,z,state,useLocks)} 绕过 {@code LevelChunk} 的
 * 第三方模组不会被捕获；{@code /setblock} 一类命令不走 {@code LevelChunk} 也不会。这类改动若发生在
 * 卸载前会随 sable 落盘保留，但从未触发过本检测的仍会被当作"未改动"丢弃 —— 这是有意的取舍：
 * 宁可漏掉少数边角，也不要给每颗小行星都留下永久存档副本。
 */
public final class AsteroidEditTracker {

    private AsteroidEditTracker() {}

    /**
     * 方块写入回调（由 {@code LevelChunkPersistenceMixin} 调用）。
     *
     * <p>只认"chunk 属于某个 plot，且该 plot 的子层级正挂在我们的活动表里"：
     * 实体化装配阶段的落块发生在子层级入表之前，因此不会把"刚生成"误判成"玩家改动"；
     * 反序列化恢复走 {@code plot.load()}（直接填 section），根本不会触发本回调。
     */
    public static void onChunkBlockChanged(LevelChunk chunk, BlockPos pos) {
        // 顺序按"最便宜的判定优先"排：全世界每次方块写入都会走到这里，
        // 普通区块靠 getPlot == null 一条就返回，不读配置、不遍历活动表。
        if (!AsteroidEntityifyService.sablePresent()) {
            return;
        }
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        LevelPlot plot = container.getPlot(chunk.getPos());
        if (plot == null) {
            return; // 不是 plot 方块：暂存区落块、普通世界方块等
        }
        if (!AsteroidEntityifyService.enabled()) {
            return;
        }
        SubLevel sub = plot.getSubLevel();
        if (!(sub instanceof ServerSubLevel serverSub)) {
            return;
        }
        long id = AsteroidEntityifier.idOf(serverSub);
        if (id < 0L) {
            return; // 不在活动表：装配期、别人的子层级、或已卸载
        }
        AsteroidPersistence.getOrLoad(level).markChanged(id);
    }

    /** 玩家挖掘方块。 */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!AsteroidEntityifyService.sablePresent() || !AsteroidEntityifyService.enabled()) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            onEdited(level, event.getPos());
        }
    }

    /** 玩家/实体放置方块。 */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!AsteroidEntityifyService.sablePresent() || !AsteroidEntityifyService.enabled()) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            onEdited(level, event.getPos());
        }
    }

    /** 爆炸波及的方块。 */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!AsteroidEntityifyService.sablePresent() || !AsteroidEntityifyService.enabled()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos pos : event.getExplosion().getToBlow()) {
            if (onEdited(level, pos)) {
                return; // 一次爆炸只可能落在一颗上，标记完即可
            }
        }
    }

    /**
     * 该位置若落在某颗活动小行星的 plot 内，就把它标记为"已改动"。
     *
     * @return 是否命中
     */
    public static boolean onEdited(ServerLevel level, BlockPos pos) {
        long id = AsteroidEntityifier.idAt(level, pos);
        if (id < 0L) {
            return false;
        }
        AsteroidPersistence.getOrLoad(level).markChanged(id);
        return true;
    }
}
