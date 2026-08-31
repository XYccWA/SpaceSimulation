package org.xyccwa.space_simulation.asteroid;

import java.util.HashSet;
import java.util.Set;

/**
 * 小行星近邻检索核心（纯数值，无 Minecraft 依赖，可独立验证）。
 *
 * 检索问题：给定玩家位置 P 与半径 R，找出"轨道穿过 P 球"的所有轨道环，
 * 并求得每环上"落入球内"的相位窗口 —— 这是静态几何（环固定、球固定），
 * 只随玩家位置 P 与半径 R 变化，不随游戏刻度变化。
 *
 * 用途（AsteroidProximityMonitor 持续监测的"静态层"）：
 *   1. 窗口逆推：从 P、R 推断可能相交的单元（轨道环）档窗口，枚举候选单元；
 *   2. 环-球相交测试：每个候选环做数值采样，求"环上与 P 距离 ≤ R"的 ν 区间，
 *      把边界 ν 转成 M（开普勒方程反向：ν→E→M），得到球内相位窗口 [mLo, mHi]；
 *      不相交的环排除 → 候选表只保留真相交环 + 其相位窗口。
 *   这样每刻度只需对候选环做相位平移判定（见 Monitor），不必每刻度解完整轨道。
 *
 * 相位窗口示例（ω≡0 的环）：
 *   环上点 pos(ν) = r(ν)·(cosν·b1 + sinν·b2)，
 *   r(ν) = a(1−e²)/(1+e·cosν)，
 *   b1 = (cosΩ, 0, sinΩ)，b2 = (−sinΩ·cosi, sini, cosΩ·cosi)。
 *   d2(ν) = |pos(ν) − P|² 的数值采样（均匀 128 点）找出 {d2 ≤ R²} 的 ν 区间边界。
 *
 * 本类提供：
 *   queryCells(px,py,pz, r, margin) —— 窗口逆推 → 候选单元表（含相位窗口）
 *   nearby(px,py,pz, r, tick, universe) —— 一次性查询：候选 → 相位判定 → 精确定位 → 距离过滤
 * 供 Monitor 与 /asteroid near 命令使用。
 */
public final class AsteroidProximity {

    /** 相位采样点数（求环-球相交区间）。128 点对 10000 级半径、~20000 级轨道足够。 */
    public static final int PHASE_SAMPLES = 128;
    private AsteroidProximity() {}

    // ---------- 轨道环几何 ----------

    /** 环面基 {b1x,b1y,b1z, b2x,b2y,b2z}（ω≡0）。 */
    public static double[] ringBasis(double omega, double i) {
        double cO = Math.cos(omega), sO = Math.sin(omega);
        double ci = Math.cos(i), si = Math.sin(i);
        return new double[]{cO, 0, sO, -sO * ci, si, cO * ci};
    }

    /** 环上 ν 处的位置（世界坐标）。 */
    public static double[] ringPoint(double nu, double a, double e, double[] b) {
        double r = a * (1 - e * e) / (1 + e * Math.cos(nu));
        double cn = Math.cos(nu), sn = Math.sin(nu);
        return new double[]{r * (cn * b[0] + sn * b[3]),
                r * (cn * b[1] + sn * b[4]),
                r * (cn * b[2] + sn * b[5])};
    }

    /** ν（弧度）→ 偏近点角 E（解开普勒逆向，对 e<1 单调）。 */
    public static double nuToE(double nu, double e) {
        double h = Math.atan2(Math.sqrt(1 - e) * Math.sin(nu / 2),
                Math.sqrt(1 + e) * Math.cos(nu / 2)) * 2.0;
        return ((h % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
    }

    /** E → 平近点角 M = E − e·sinE（与 AsteroidOrbit.solveKepler 互逆）。 */
    public static double eToM(double E, double e) {
        double m = E - e * Math.sin(E);
        return ((m % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
    }

    // ---------- 候选单元窗口逆推 ----------

    /**
     * 从 P、R 推断可能相交的单元窗口（超集，含 margin 余量）。
     * 返回：{aLo, aHi, eLo, eHi? } —— 简化为直接枚举档区间并回调 accumulate：
     * 对每个 (aIdx, eIdx, iIdx, oIdx) 的档，若粗条件满足则加入。
     * 粗条件（均只筛"明显不可能"，保超集）：
     *   - 径向：轨道近/远拱点跨过 [rP−R, rP+R]：a(1+e_hi) ≥ rP−R 且 a(1−e_hi) ≤ rP+R
     *   - 高度：轨道最高点够得到球：a·sin(i_hi) ≥ |py|−R
     *   - 方位：轨道方位跨度 [Ω−α, Ω+α]（α 保守取 i_max）与球方位窗口 [az−θ, az+θ] 有交
     * 传入 cellCollector 回调 (aIdx,eIdx,iIdx,oIdx)，返回候选单元键。
     */
    public static long[] queryCells(AsteroidUniverse u,
                                    double px, double py, double pz, double r, double margin) {
        double reff = r * (1.0 + margin);
        double rP = Math.sqrt(px * px + py * py + pz * pz);
        // 快速空：球与整个环带分离（带的最小/最大轨道距离之外）
        double qMin = u.innerRadius * (1.0 - u.maxEccentricity);
        double qMax = u.outerRadius * (1.0 + u.maxEccentricity);
        if (rP + reff < qMin || rP - reff > qMax) return new long[0];

        // 注意：轨道环在 xz 平面的投影是包围原点的椭圆 → 环上点方位覆盖全 360°，
        // 不能按 Ω 方位窗口排除候选（会漏报）。oIdx 全遍历，精测由 phaseWindow 剔除。
        double da = (u.outerRadius - u.innerRadius) / u.A_BINS;
        double dE = u.maxEccentricity / u.E_BINS;
        double dI = u.maxInclinationRad / u.I_BINS;

        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        for (int eIdx = 0; eIdx < u.E_BINS; eIdx++) {
            double eLo = eIdx * dE, eHi = (eIdx + 1) * dE;
            for (int aIdx = 0; aIdx < u.A_BINS; aIdx++) {
                double aLoR = u.innerRadius + aIdx * da;
                double aHiR = aLoR + da;
                // 近拱 q=a(1-e)：档内最小近拱 aLoR(1-eHi) 若 > rP+reff → 整档在球外
                if (aLoR * (1 - eHi) > rP + reff) continue;
                // 远拱 Q=a(1+e)：档内最大远拱 aHiR(1+eHi) 若 < rP-reff → 整档在球内侧
                if (aHiR * (1 + eHi) < rP - reff) continue;
                for (int iIdx = 0; iIdx < u.I_BINS; iIdx++) {
                    double iHi = (iIdx + 1) * dI;
                    // 高度粗筛：轨道最大 |y| = a·sin(iHi)（外层上限 aHiR 保超集）
                    if (aHiR * Math.sin(iHi) < Math.abs(py) - reff) continue;
                    // oIdx 全遍历（环上方位全覆盖，不能按 Ω 滤）
                    for (int oIdx = 0; oIdx < u.O_BINS; oIdx++) {
                        out.add(AsteroidUniverse.packCell(aIdx, eIdx, iIdx, oIdx));
                    }
                }
            }
        }
        long[] arr = new long[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    /** 角度标准化到 [0, 2π)。 */
    private static double normAngle(double a) {
        return ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
    }

    // ---------- 环-球相交测试 + 相位窗口 ----------

    /**
     * 单元（轨道环）与球 (P, R) 的相交测试，返回球内相位窗口。
     *
     * @return null = 不相交；否则 {mLo, mHi, wrap(0/1)}：
     *         相位 m ∈ [0,2π)，wrap=0 → 单区间 [mLo, mHi]；wrap=1 → 两段 [0,mHi]∪[mLo,2π)。
     */
    public static double[] phaseWindow(AsteroidUniverse u, long cellKey,
                                       double px, double py, double pz, double r) {
        double[] el = u.cellElements(cellKey);
        double a = el[0], e = el[1], i = el[2], omega = el[3];
        double[] b = ringBasis(omega, i);
        double r2 = r * r;

        // 1) 初扫：32 均匀点找全局最小距离采样点（相交判定不依赖采样点命中球内）
        int S = 32;
        double bestNu = 0, bestD2 = Double.MAX_VALUE;
        for (int s = 0; s < S; s++) {
            double nu = 2 * Math.PI * s / S;
            double d2 = dist2(nu, a, e, b, px, py, pz);
            if (d2 < bestD2) { bestD2 = d2; bestNu = nu; }
        }
        // 2) 邻域递归细化最小距离（2 层 × 8 点，区间逐层缩小；初扫间距内必含真极小）
        double h = 2 * Math.PI / S;
        double lo = bestNu - h, hi = bestNu + h;
        for (int level = 0; level < 2; level++) {
            double bs = Double.MAX_VALUE, bx = lo;
            for (int s = 0; s <= 8; s++) {
                double x = lo + (hi - lo) * s / 8.0;
                double d2 = dist2(x, a, e, b, px, py, pz);
                if (d2 < bs) { bs = d2; bx = x; }
            }
            double hh = (hi - lo) / 8.0;
            lo = bx - hh; hi = bx + hh;
        }
        double minNu = 0, minD2 = Double.MAX_VALUE;
        for (int s = 0; s <= 8; s++) {
            double x = lo + (hi - lo) * s / 8.0;
            double d2 = dist2(x, a, e, b, px, py, pz);
            if (d2 < minD2) { minD2 = d2; minNu = x; }
        }
        // 精判：环（的最小距离）在球外 → 不相交。相切（minD2≈r2）视为无球内颗。
        if (minD2 > r2 + 1e-6) return null;

        // 3) 从最接近点向两侧步进扫描"出界"零点（d2 从 ≤r2 到 >r2）——窗口远窄于步长，首步即出界
        double step = 2 * Math.PI / 32;
        Double edgeHi = scanExit(a, e, b, px, py, pz, r2, minNu, +step);
        Double edgeLo = scanExit(a, e, b, px, py, pz, r2, minNu, -step);
        if (edgeHi == null || edgeLo == null) {
            // 某一侧整圈都在球内 → 几乎整环 ∈ 球 → 保守全窗口（精确过滤兜底，绝无漏报）
            return new double[]{0, 2 * Math.PI, 0};
        }
        // 窗口绝对区间 [edgeLo, edgeHi]（edgeLo ≤ minNu ≤ edgeHi）；跨 0 由绝对位置判断
        boolean wrap = edgeLo < 0 || edgeHi >= 2 * Math.PI;
        double mIn = eToM(nuToE(normAngle(edgeLo), e), e);
        double mOut = eToM(nuToE(normAngle(edgeHi), e), e);
        return new double[]{mIn, mOut, wrap ? 1 : 0};
    }

    /** 从 nuStart 沿 dir 步进扫描（至多一整圈），找第一个 d2>r2 的出界区间并二分零点；整圈无出界返回 null。 */
    private static Double scanExit(double a, double e, double[] b,
                                   double px, double py, double pz, double r2,
                                   double nuStart, double dir) {
        double prev = nuStart, prevD = dist2(prev, a, e, b, px, py, pz);
        double s = nuStart + dir;
        for (int k = 1; k <= 32; k++) {
            double d = dist2(s, a, e, b, px, py, pz);
            if (prevD <= r2 && d > r2) return bisectBoundary(prev, s, a, e, b, px, py, pz, r2);
            prev = s; prevD = d; s += dir;
        }
        return null;
    }

    /** 在区间端点（一侧 ≤r2 一侧 >r2）之间二分 d2=r2 的零点。 */
    private static double bisectBoundary(double nu1, double nu2, double a, double e, double[] b,
                                         double px, double py, double pz, double r2) {
        double lo = Math.min(nu1, nu2), hi = Math.max(nu1, nu2);
        double flo = dist2(lo, a, e, b, px, py, pz) - r2;
        for (int k = 0; k < 48; k++) {
            double mid = (lo + hi) / 2;
            double fm = dist2(mid, a, e, b, px, py, pz) - r2;
            if (flo * fm <= 0) hi = mid; else { lo = mid; flo = fm; }
        }
        return (lo + hi) / 2;
    }

    /** 环上 ν 处到 (P) 的距离平方。 */
    private static double dist2(double nu, double a, double e, double[] b,
                                double px, double py, double pz) {
        double[] p = ringPoint(nu, a, e, b);
        double dx = p[0] - px, dy = p[1] - py, dz = p[2] - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    // ---------- 相位判定（给定 tick，某颗 id 是否在球内？直接由窗口表推导） ----------

    /**
     * 候选环在 tick 时刻的"球内颗相位区间" → 环内序号区间（可能两段）。
     * 由静态窗口 [mLo,mHi]（wrap）平移 n·tick 得到：相位窗口随时间整体转动。
     * 返回 {lo1, hi1, lo2, hi2, nSeg}：nSeg=1 时区间 [lo1,hi1]；nSeg=2 时 [lo1,hi1]∪[lo2,hi2]。
     * 区间按"环内相位网格"（颗相位 = 2π(i+u)/k）反解，含 ±容差 1 保不漏。
     */
    public static int[] ringIdxWindow(AsteroidUniverse u, double[] phaseWin, int k,
                                      double n, long tick) {
        double scale = k / (2 * Math.PI);
        double shift = (n * tick) % (2 * Math.PI);
        // 展开相位窗口为 1~2 段（每段独立判断，平移后可能跨 0）
        double[][] segs = (phaseWin[2] > 0.5)
                ? new double[][]{{0, phaseWin[1]}, {phaseWin[0], 2 * Math.PI}}
                : new double[][]{{phaseWin[0], phaseWin[1]}};
        java.util.ArrayList<int[]> ranges = new java.util.ArrayList<>();
        for (double[] seg : segs) {
            double w = seg[1] - seg[0];
            if (w >= 2 * Math.PI - 1e-9) { // 整环在球内
                ranges.add(new int[]{0, k - 1});
                continue;
            }
            if (w <= 1e-12) continue;
            double lo = normAngle(seg[0] - shift);
            double hi = normAngle(seg[1] - shift);
            if (lo <= hi) {
                int iLo = Math.max(0, (int) Math.floor(lo * scale) - 1);
                int iHi = Math.min(k - 1, (int) Math.ceil(hi * scale) + 1);
                if (iLo <= iHi) ranges.add(new int[]{iLo, iHi});
            } else { // 段跨 0：两段 [lo, 2π) 与 [0, hi]
                int iLo = Math.max(0, (int) Math.floor(lo * scale) - 1);
                int iHi = Math.min(k - 1, (int) Math.ceil(hi * scale) + 1);
                if (iLo <= k - 1) ranges.add(new int[]{iLo, k - 1});
                if (0 <= iHi) ranges.add(new int[]{0, iHi});
            }
        }
        if (ranges.isEmpty()) return new int[]{0, -1, 0, -1, 0}; // 空
        if (ranges.size() == 1) {
            int[] a = ranges.get(0);
            return new int[]{a[0], a[1], 0, -1, 1};
        }
        int[] a = ranges.get(0), b = ranges.get(1);
        return new int[]{a[0], a[1], b[0], b[1], 2};
    }

    // ---------- 一次性查询 ----------

    /**
     * 一次性近邻查询：P 球内全部小行星 id（精确位置过滤）。
     * 用于 /asteroid near 命令与独立验证。
     */
    public static Set<Long> nearby(AsteroidUniverse u, double px, double py, double pz,
                                   double radius, long tick) {
        Set<Long> out = new HashSet<>();
        if (radius <= 0) return out;
        long[] cells = queryCells(u, px, py, pz, radius, 0.15);
        double r2 = radius * radius;
        for (long cellKey : cells) {
            double[] el = u.cellElements(cellKey);
            double n = el[4];
            double[] win = phaseWindow(u, cellKey, px, py, pz, radius * 1.15);
            if (win == null) continue;
            int[] rw = ringIdxWindow(u, win, u.k, n, tick);
            if (rw[4] == 1) {
                collectIds(u, cellKey, rw[0], rw[1], px, py, pz, r2, tick, out);
            } else {
                collectIds(u, cellKey, rw[0], rw[1], px, py, pz, r2, tick, out);
                collectIds(u, cellKey, rw[2], rw[3], px, py, pz, r2, tick, out);
            }
        }
        return out;
    }

    private static void collectIds(AsteroidUniverse u, long cellKey, int lo, int hi,
                                   double px, double py, double pz, double r2, long tick,
                                   Set<Long> out) {
        for (int idx = lo; idx <= hi; idx++) {
            long id = u.idOf(cellKey, idx);
            double[] p = u.orbitOf(id).positionAt(tick);
            double dx = p[0] - px, dy = p[1] - py, dz = p[2] - pz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= r2 && d2 >= 0) out.add(id);
        }
    }
}