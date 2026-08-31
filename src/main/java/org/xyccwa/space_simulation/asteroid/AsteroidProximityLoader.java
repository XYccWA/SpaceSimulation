package org.xyccwa.space_simulation.asteroid;

import java.util.Set;

/**
 * 加载逻辑（无实体化）—— 预加载索引 + 强加载每 tick 检索。
 *
 * 分级：
 *   预加载（preloadRadius，默认 50000，远大于玩家可跑出范围）：范围内的小行星写成
 *       "索引"（真相交环表 + 每环相位窗口 + 每环当前命中 ringIdx 区间）。
 *       索引是静态几何（环-球相交只随玩家位置 P 变化，不随游戏刻度变化）：
 *       仅玩家位移超阈值（preloadRadius×0.125）时整轮重建，重建分帧（每 tick 只精测
 *       FRAME 个候选）绝不阻塞主线程；预载区间按 preloadIntervalTicks 间隔平移刷新
 *       （跟踪轨道相位移动，"按时间间隔检索"）。重建期间旧索引保持可用，
 *       因预载范围远大于玩家位移，玩家不会跑出旧索引覆盖区。
 *   强加载（strongRadius，默认 2000，必须 &lt; 预加载）：每 tick 直接从索引检索，
 *       只对强载档相位窗口命中的颗做精确位置 + 距离过滤（颗少，~0.2ms/tick）。
 *
 * 全程确定性；在服务端 tick 线程同步调用 update(...)（由 AsteroidProximityService 驱动）。
 */
public final class AsteroidProximityLoader {

    private final AsteroidUniverse universe;

    /** 预加载半径（块）。 */
    public final double preloadRadius;
    /** 强加载半径（块），必须小于 preloadRadius。 */
    public final double strongRadius;
    /** 预载区间平移刷新的间隔（tick）——"按时间间隔检索"；强载档不受节流，每 tick。 */
    public final int preloadIntervalTicks;

    /** 每 tick 分帧精测的候选单元数（主线程单 tick 分摊上限）。 */
    public static final int FRAME = 1024;

    /** 内部监测核心：band0=预载（区间索引）、band1=强载（精确集合）。 */
    private final AsteroidProximityMonitor monitor;

    // 实测统计
    private long lastUpdateNs;

    public AsteroidProximityLoader(AsteroidUniverse universe,
                                   double preloadRadius, double strongRadius,
                                   int preloadIntervalTicks) {
        if (strongRadius >= preloadRadius) {
            throw new IllegalArgumentException("强加载范围(" + strongRadius
                    + ")必须小于预加载范围(" + preloadRadius + ")");
        }
        this.universe = universe;
        this.preloadRadius = preloadRadius;
        this.strongRadius = strongRadius;
        this.preloadIntervalTicks = Math.max(1, preloadIntervalTicks);
        this.monitor = new AsteroidProximityMonitor(universe, preloadIntervalTicks);
    }

    /** 重置（首帧或换宇宙/换玩家）。 */
    public void reset(double px, double py, double pz, long tick) {
        monitor.reset(px, py, pz, radii(), tick);
    }

    /**
     * 每 tick 更新（服务端 tick 线程）：
     * 位移超阈值 → Monitor 启动分帧整轮重建（期间旧索引可用）；预载区间按间隔平移；
     * 强载每 tick 精确检索。
     */
    public void update(double px, double py, double pz, long tick) {
        long t0 = System.nanoTime();
        monitor.update(px, py, pz, radii(), tick);
        lastUpdateNs = System.nanoTime() - t0;
    }

    private double[] radii() {
        return new double[]{preloadRadius, strongRadius};
    }

    // ---------- 索引与集合（实体化层/其它用途读取） ----------

    /** 强加载当前集合（每 tick 更新，勿改）。 */
    public Set<Long> strongSet() {
        return monitor.bandSet(1);
    }

    /** 强加载当前颗数。 */
    public int strongCount() {
        return strongSet().size();
    }

    /** 预载索引环表（真相交环 cellKey）。 */
    public long[] preloadCells() {
        return monitor.preloadCellKeys();
    }

    /** 预载索引区间表：[cellKey, lo1, hi1, lo2, hi2, nSeg]×N（高效遍历）。 */
    public long[][] preloadRanges() {
        return monitor.preloadRanges();
    }

    /** 预载索引当前环数。 */
    public int preloadCellCount() {
        return monitor.cellCount();
    }

    /** 预载集合（惰性展开，实体化层按需调用）。 */
    public Set<Long> preloadSet() {
        return monitor.bandPreloadSet();
    }

    // ---------- 进出事件（实时监测） ----------

    /** 上一步进入预载范围（band0）的颗 id（消费；按预载区间刷新间隔粒度）。 */
    public long[] pollEnteredPreload() {
        return monitor.pollEntered(0);
    }

    /** 上一步离开预载范围（band0）的颗 id（消费）。 */
    public long[] pollLeftPreload() {
        return monitor.pollLeft(0);
    }

    /** 上一步进入强载范围（band1）的颗 id（消费；每 tick 粒度）。 */
    public long[] pollEnteredStrong() {
        return monitor.pollEntered(1);
    }

    /** 上一步离开强载范围（band1）的颗 id（消费）。 */
    public long[] pollLeftStrong() {
        return monitor.pollLeft(1);
    }

    // ---------- 实测统计 ----------

    /** 预载索引是否正在分帧重建中。 */
    public boolean indexBuilding() {
        return monitor.building();
    }

    /** 重建进度：已精测候选帧数。 */
    public int indexFrame() {
        return monitor.buildFrame();
    }

    /** 重建进度：本次候选总数。 */
    public int indexTotal() {
        return monitor.buildTotal();
    }

    /** 最近一次 update 耗时 ns。 */
    public long lastUpdateNs() {
        return lastUpdateNs;
    }
}