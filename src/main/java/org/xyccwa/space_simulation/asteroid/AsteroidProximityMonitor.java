package org.xyccwa.space_simulation.asteroid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 持续近邻监测器 —— "整个运行过程不间断检索、实时监测进出"的实体化前置层。
 *
 * 分级半径模型（预加载档 / 强加载档，实际半径运行时可动态变化）：
 *   band 0（预载档，最大半径）：集合用"相位区间"表示（每真相交环存当前命中的
 *       环内序号区间），不逐颗解位置；进出事件 = 区间 diff（O(1)/环）。
 *   band 1..n（强载档，较小半径）：每环存"本档半径的相位窗口"，动态层只对本档
 *       窗口命中颗做精确位置 + 距离过滤（颗少：强载球内几十~几百颗）。
 *
 * 分帧重建：位移超阈值或半径变化时启动，每 tick 处理一帧候选（FRAME=4096），
 * 期间动态层用旧表（滞后轻微）；完成时 phaseWindow 精测剔除不相交环并原子切换；
 * 切换后首 tick 基线化 prev（跳过假"进入"事件）。
 * 全程确定性；服务端 tick 线程同步调用。
 */
public final class AsteroidProximityMonitor {

    private final AsteroidUniverse u;

    /** 预载档（band0）区间平移刷新间隔（tick）。强载档不受节流，每 tick 检索。 */
    private final int preloadIntervalTicks;

    private double[] radii = new double[0];
    private double cx, cy, cz;
    private double lx, ly, lz;
    private boolean built = false;

    // 静态层（当前生效）：真相交环 + 每环每档相位窗口
    private long[] cells = new long[0];
    private double[] cellNs = new double[0];      // [ci] → n
    private double[][][] cellBandWins = new double[0][][]; // [ci][band] → phaseWin（null=该档不相交）

    // 分帧重建状态
    private boolean building = false;
    private int frameCursor = 0;
    private long[] newCells = new long[0];
    private double[] newNs = new double[0];
    private double[][][] newBandWins = new double[0][][];
    private boolean firstAfterRebuild = false;
    /** 每帧精测的候选数。 */
    public static final int FRAME = 1024;

    // 预载档逐环命中区间（动态层刷新）：[lo1, hi1, lo2, hi2, nSeg]
    private int[][] curRanges = new int[0][];
    private int[][] prevRanges = new int[0][];

    // 强载档精确集合（band>=1）与上 tick（事件 diff）
    private final List<Set<Long>> cur = new ArrayList<>();
    private final List<Set<Long>> prev = new ArrayList<>();
    private final List<List<Long>> entered = new ArrayList<>();
    private final List<List<Long>> left = new ArrayList<>();

    public AsteroidProximityMonitor(AsteroidUniverse universe) {
        this(universe, 20);
    }

    /** @param preloadIntervalTicks 预载档区间平移刷新的间隔（tick），强载档不受节流。 */
    public AsteroidProximityMonitor(AsteroidUniverse universe, int preloadIntervalTicks) {
        this.u = universe;
        this.preloadIntervalTicks = Math.max(1, preloadIntervalTicks);
    }

    // ---------- 对外接口 ----------

    /** 重置（首帧或换宇宙）。 */
    public void reset(double px, double py, double pz, double[] bandRadii, long tick) {
        this.radii = bandRadii.clone();
        this.cx = px; this.cy = py; this.cz = pz;
        this.lx = px; this.ly = py; this.lz = pz;
        this.built = false;
        this.building = false;
        this.frameCursor = 0;
        this.firstAfterRebuild = false;
        cur.clear(); prev.clear(); entered.clear(); left.clear();
        for (double r : radii) {
            cur.add(new HashSet<>());
            prev.add(new HashSet<>());
            entered.add(new ArrayList<>());
            left.add(new ArrayList<>());
        }
        update(px, py, pz, bandRadii, tick);
    }

    /** 每 tick 更新。 */
    public void update(double px, double py, double pz, double[] bandRadii, long tick) {
        if (!sameRadii(bandRadii)) {
            this.radii = bandRadii.clone();
            cur.clear(); prev.clear(); entered.clear(); left.clear();
            for (double r : radii) {
                cur.add(new HashSet<>());
                prev.add(new HashSet<>());
                entered.add(new ArrayList<>());
                left.add(new ArrayList<>());
            }
            built = false;
        }
        this.cx = px; this.cy = py; this.cz = pz;

        double maxR = 0;
        for (double r : radii) maxR = Math.max(maxR, r);

        double thr = maxR * 0.125;
        double dx = px - lx, dy = py - ly, dz = pz - lz;
        boolean moved = dx * dx + dy * dy + dz * dz > thr * thr;
        if ((!built || moved) && !building) {
            startRebuild(px, py, pz);
            lx = px; ly = py; lz = pz;
        }
        if (building) stepRebuild();

        // 事件缓冲清空 + 强载档 prev 快照
        for (int b = 0; b < radii.length; b++) {
            prev.get(b).clear();
            prev.get(b).addAll(cur.get(b));
            cur.get(b).clear();
            entered.get(b).clear();
            left.get(b).clear();
        }
        int[][] tmp = prevRanges;
        prevRanges = curRanges;
        curRanges = tmp;
        if (prevRanges.length != cells.length) prevRanges = new int[cells.length][];
        if (curRanges.length != cells.length) curRanges = new int[cells.length][];
        for (int i = 0; i < cells.length; i++) {
            if (curRanges[i] == null) curRanges[i] = new int[5];
            if (prevRanges[i] == null) prevRanges[i] = new int[]{0, -1, 0, -1, 0};
        }

        double[] r2s = new double[radii.length];
        for (int b = 0; b < radii.length; b++) r2s[b] = radii[b] * radii[b];

        // 动态层：每环每档。预载档（band0）区间按间隔平移刷新（节流）；强载档每 tick。
        boolean preTick = tick % preloadIntervalTicks == 0;
        for (int ci = 0; ci < cells.length; ci++) {
            long cellKey = cells[ci];
            double n = cellNs[ci];
            if (preTick) {
                int[] win0 = AsteroidProximity.ringIdxWindow(u, cellBandWins[ci][0], u.k, n, tick);
                int[] r0 = curRanges[ci];
                r0[0] = win0[0]; r0[1] = win0[1]; r0[2] = win0[2]; r0[3] = win0[3]; r0[4] = win0[4];
            }
            // 强载档（band>=1）：用本档相位窗口 → 只对本档命中颗解位置
            for (int b = 1; b < radii.length; b++) {
                double[] win = cellBandWins[ci][b];
                if (win == null) continue; // 本档不相交
                int[] rw = AsteroidProximity.ringIdxWindow(u, win, u.k, n, tick);
                if (rw[4] == 0) continue;
                collectBand(cellKey, rw[0], rw[1], tick, r2s, b);
                if (rw[4] == 2) collectBand(cellKey, rw[2], rw[3], tick, r2s, b);
            }
        }

        // 重建切换后首 tick：基线化 prev（跳过假事件）
        if (firstAfterRebuild) {
            for (int ci = 0; ci < cells.length; ci++) {
                int[] cr = curRanges[ci];
                int[] pr = prevRanges[ci];
                pr[0] = cr[0]; pr[1] = cr[1]; pr[2] = cr[2]; pr[3] = cr[3]; pr[4] = cr[4];
            }
            for (int b = 0; b < radii.length; b++) {
                prev.get(b).clear();
                prev.get(b).addAll(cur.get(b));
            }
            firstAfterRebuild = false;
            return;
        }

        // 事件：预载档事件按刷新间隔（与 band0 区间刷新同步）；强载档每 tick
        if (preTick) eventForBand0();
        for (int b = 1; b < radii.length; b++) eventForBand(b);
    }

    /** 预载档（band 0）展开集合：由当前区间惰性生成（实体化层按需调用）。 */
    public Set<Long> bandPreloadSet() {
        Set<Long> out = new HashSet<>();
        for (int ci = 0; ci < cells.length; ci++) {
            int[] r = curRanges[ci];
            if (r == null || r[4] == 0) continue;
            expandRange(cells[ci], r[0], r[1], out);
            if (r[4] == 2) expandRange(cells[ci], r[2], r[3], out);
        }
        return out;
    }

    /** 某强载档（band>=1）当前精确 id 集合（实体化层读取，勿改）。 */
    public Set<Long> bandSet(int band) {
        return cur.get(band);
    }

    /** 预载索引环表（当前真相交环 cellKey，与 preloadRanges 行序一致）。 */
    public long[] preloadCellKeys() {
        return cells;
    }

    /** 预载档命中区间表：[cellKey, lo1, hi1, lo2, hi2, nSeg]×N。 */
    public long[][] preloadRanges() {
        long[][] out = new long[cells.length][];
        for (int ci = 0; ci < cells.length; ci++) {
            int[] r = curRanges[ci];
            out[ci] = new long[]{cells[ci], r[0], r[1], r[2], r[3], r[4]};
        }
        return out;
    }

    /** 当前真相交环单元数（性能观测）。 */
    public int cellCount() {
        return cells.length;
    }

    /** 索引是否正在分帧重建中。 */
    public boolean building() {
        return building;
    }

    /** 重建进度：已精测候选数。 */
    public int buildFrame() {
        return frameCursor;
    }

    /** 重建进度：本次候选总数。 */
    public int buildTotal() {
        return newCells.length;
    }

    /** 上一步进入某档的颗 id（消费）。 */
    public long[] pollEntered(int band) {
        return drain(entered.get(band));
    }

    /** 上一步离开某档的颗 id（消费）。 */
    public long[] pollLeft(int band) {
        return drain(left.get(band));
    }

    // ---------- 内部 ----------

    private void startRebuild(double px, double py, double pz) {
        double maxR = maxRadius();
        newCells = AsteroidProximity.queryCells(u, px, py, pz, maxR, 0.15);
        newNs = new double[newCells.length];
        newBandWins = new double[newCells.length][radii.length][];
        frameCursor = 0;
        building = true;
    }

    /** 每 tick 处理一帧：逐档相位窗口精测；完成后剔除不相交环并切换。 */
    private void stepRebuild() {
        int end = Math.min(newCells.length, frameCursor + FRAME);
        for (int i = frameCursor; i < end; i++) {
            double[] el = u.cellElements(newCells[i]);
            newNs[i] = el[4];
            for (int b = 0; b < radii.length; b++) {
                newBandWins[i][b] = AsteroidProximity.phaseWindow(u, newCells[i], cx, cy, cz, radii[b] * 1.15);
            }
        }
        frameCursor = end;
        if (frameCursor >= newCells.length) {
            // 保留与最大档（band 0）相交的环
            int n = 0;
            for (int i = 0; i < newCells.length; i++) if (newBandWins[i][0] != null) n++;
            long[] c2 = new long[n];
            double[] n2 = new double[n];
            double[][][] w2 = new double[n][][];
            int k = 0;
            for (int i = 0; i < newCells.length; i++) {
                if (newBandWins[i][0] == null) continue;
                c2[k] = newCells[i];
                n2[k] = newNs[i];
                w2[k] = newBandWins[i];
                k++;
            }
            cells = c2;
            cellNs = n2;
            cellBandWins = w2;
            curRanges = new int[cells.length][];
            prevRanges = new int[cells.length][];
            building = false;
            built = true;
            firstAfterRebuild = true;
        }
    }

    private double maxRadius() {
        double m = 0;
        for (double r : radii) m = Math.max(m, r);
        return m;
    }

    /** 强载档精确收集：本档窗口命中区间 [lo,hi] 的颗 → 位置 → 距离过滤 → band 集合。 */
    private void collectBand(long cellKey, int lo, int hi, long tick, double[] r2s, int band) {
        if (lo > hi) return;
        double r2 = r2s[band];
        for (int idx = lo; idx <= hi; idx++) {
            long id = u.idOf(cellKey, idx);
            double[] p = u.orbitOf(id).positionAt(tick);
            double dx = p[0] - cx, dy = p[1] - cy, dz = p[2] - cz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= r2) cur.get(band).add(id);
        }
    }

    /** 预载档（band0）事件：区间 diff → 进入/离开颗。 */
    private void eventForBand0() {
        List<Long> en = entered.get(0), lv = left.get(0);
        for (int ci = 0; ci < cells.length; ci++) {
            int[] cr = curRanges[ci];
            int[] pr = prevRanges[ci];
            if (cr == null || pr == null) continue;
            if (cr[4] == 0 && pr[4] == 0) continue;
            diffRanges(cells[ci], cr, pr, en, lv);
        }
    }

    private void diffRanges(long cellKey, int[] curSeg, int[] prevSeg, List<Long> en, List<Long> lv) {
        markDiff(cellKey, curSeg, prevSeg, en);
        markDiff(cellKey, prevSeg, curSeg, lv);
    }

    /** a 的段中 b 未覆盖的 idx（区间差，展开成颗）。 */
    private void markDiff(long cellKey, int[] a, int[] b, List<Long> out) {
        int nA = a[4] == 0 ? 0 : 1;
        if (a[4] == 2) nA = 2;
        int nB = b[4] == 0 ? 0 : 1;
        if (b[4] == 2) nB = 2;
        for (int i = 0; i < nA; i++) {
            int lo = a[i * 2], hi = a[i * 2 + 1];
            if (lo > hi) continue;
            for (int idx = lo; idx <= hi; idx++) {
                boolean covered = false;
                for (int j = 0; j < nB; j++) {
                    if (idx >= b[j * 2] && idx <= b[j * 2 + 1]) { covered = true; break; }
                }
                if (!covered) out.add(u.idOf(cellKey, idx));
            }
        }
    }

    private void eventForBand(int b) {
        List<Long> en = entered.get(b), lv = left.get(b);
        for (long id : cur.get(b)) {
            if (!prev.get(b).contains(id)) en.add(id);
        }
        for (long id : prev.get(b)) {
            if (!cur.get(b).contains(id)) lv.add(id);
        }
    }

    private void expandRange(long cellKey, int lo, int hi, Set<Long> out) {
        if (lo > hi) return;
        for (int idx = lo; idx <= hi; idx++) {
            out.add(u.idOf(cellKey, idx));
        }
    }

    private boolean sameRadii(double[] r) {
        if (r.length != radii.length) return false;
        for (int i = 0; i < r.length; i++) if (r[i] != radii[i]) return false;
        return true;
    }

    private static long[] drain(List<Long> list) {
        long[] out = new long[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        list.clear();
        return out;
    }
}