package org.xyccwa.space_simulation.asteroid;

/**
 * 程序化小行星 —— 单颗不可变开普勒椭圆轨道（质点模型）。
 *
 * 存储的是经典六根数：半长轴 a、离心率 e、轨道倾角 i、升交点经度 Ω、
 * 近心点幅角 ω、t=0 平近点角 M0，外加系统统一引力参数 μ。
 * 任意游戏刻度 tick 的世界坐标由开普勒方程确定性解出：
 *   M(t) = M0 + n·t（n = √(μ/a³) 平均角速度）
 *   E − e·sinE = M（开普勒方程，牛顿迭代）
 *   r = a(1 − e·cosE)，真近点角 ν，纬度幅角 u = ω + ν
 *   x = r(cosΩ·cosu − sinΩ·sinu·cosi)
 *   y = r·sini·sinu
 *   z = r(sinΩ·cosu + cosΩ·sinu·cosi)
 * 坐标系与模组一致：原点 = 中心天体（太阳），y 为竖直轴，单位 = 块(game tick)。
 *
 * 纯 double 数学、无 Minecraft 依赖、无任何状态 —— 同一 (index, tick) 永远
 * 得到同一位置，跨会话可复现；本类不参与任何网格/分桶/存在性判定。
 */
public final class AsteroidOrbit {

    private static final double TWO_PI = 2.0 * Math.PI;

    /** 小行星唯一身份（编号，0..totalCount-1）。 */
    public final long index;
    /** 半长轴（块）。 */
    public final double a;
    /** 离心率 0 ≤ e < 1（椭圆约束，本系统取 0..~0.35）。 */
    public final double e;
    /** 轨道倾角（弧度）。 */
    public final double i;
    /** 升交点经度 Ω（弧度）。 */
    public final double omega;
    /** 近心点幅角 ω（弧度）。 */
    public final double argP;
    /** t = 0 时的平近点角（弧度）—— 只是相位，不参与身份。 */
    public final double m0;
    /** 引力参数（块³/tick²），全系统统一。 */
    public final double mu;

    public AsteroidOrbit(long index, double a, double e, double i, double omega,
                         double argP, double m0, double mu) {
        this.index = index;
        this.a = a;
        this.e = e;
        this.i = i;
        this.omega = omega;
        this.argP = argP;
        this.m0 = m0;
        this.mu = mu;
    }

    /** 平均角速度 n = √(μ/a³)（弧度/tick）。 */
    public double meanMotion() {
        return Math.sqrt(mu / (a * a * a));
    }

    /** 公转周期（tick）；开普勒第三定律 T = 2π√(a³/μ)。 */
    public double periodTicks() {
        return TWO_PI / meanMotion();
    }

    /** 解线性开普勒方程 E − e·sinE = M（牛顿迭代；e < 0.35 时 8 次迭代达双精度）。M 任意大，内部归一化。 */
    public static double solveKepler(double m, double e) {
        double M = ((m % TWO_PI) + TWO_PI) % TWO_PI;
        double E = M + e * Math.sin(M); // e 较小时的优良初值
        for (int k = 0; k < 8; k++) {
            E -= (E - e * Math.sin(E) - M) / (1.0 - e * Math.cos(E));
        }
        return E;
    }

    /** 真近点角 ν（atan2 形式，无象限分支）。 */
    public static double trueAnomaly(double e, double E) {
        return 2.0 * Math.atan2(Math.sqrt(1.0 + e) * Math.sin(E / 2.0),
                               Math.sqrt(1.0 - e) * Math.cos(E / 2.0));
    }

    /** 某游戏刻度下距中心天体距离（块）：r = a(1 − e·cosE)。 */
    public double radiusAt(long tick) {
        double E = solveKepler(m0 + meanMotion() * tick, e);
        return a * (1.0 - e * Math.cos(E));
    }

    /** 某游戏刻度下的世界坐标（块，double 精度），确定性无状态。返回 {x, y, z}。 */
    public double[] positionAt(long tick) {
        double M = m0 + meanMotion() * tick;
        double E = solveKepler(M, e);
        double nu = trueAnomaly(e, E);
        double r = a * (1.0 - e * Math.cos(E));
        double u = argP + nu; // 纬度幅角
        double cu = Math.cos(u), su = Math.sin(u);
        double co = Math.cos(omega), so = Math.sin(omega);
        double ci = Math.cos(i), si = Math.sin(i);
        return new double[]{
                r * (co * cu - so * su * ci),
                r * (si * su),
                r * (so * cu + co * su * ci)
        };
    }

    /** 当前平近点角（弧度，已归一化到 [0, 2π)）。 */
    public double meanAnomalyAt(long tick) {
        double M = m0 + meanMotion() * tick;
        return ((M % TWO_PI) + TWO_PI) % TWO_PI;
    }

    /** 轨道速度标量（vis-viva：v² = μ(2/r − 1/a)，块/tick）。 */
    public double speedAt(long tick) {
        double r = radiusAt(tick);
        return Math.sqrt(mu * (2.0 / r - 1.0 / a));
    }
}