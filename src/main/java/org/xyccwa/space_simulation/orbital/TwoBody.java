package org.xyccwa.space_simulation.orbital;

/**
 * 纯数学二体问题（零 Minecraft 依赖，可独立数值验证）。
 *
 * 用途：玩家离线期间不做实体 tick，只保存"退出那一刻的轨道根数"，登录时按服务器
 * tick 时间差解析传播出位置与速度（见 OrbitalPersistence）。在线时走数值积分
 * （见 OrbitalPhysics），两者共用同一引力参数 μ。
 *
 * 约定与模组一致：中心天体（太阳）在世界原点，y 为竖直轴，单位 = 块 / game tick。
 * 时间 t0 = 0 表示"根数对应时刻"，其后任意 dt（tick）由开普勒方程确定性解出：
 *   M(t) = M0 + n·dt（n = √(μ/|a|³)）
 *   椭圆：E − e·sinE = M（牛顿迭代）
 *   双曲：e·sinhH − H = M（牛顿迭代）
 *   r = p/(1 + e·cosν)，p = a(1 − e²)（双曲时 a < 0 但 p > 0，同一公式成立）
 *   近焦点系 (x 指向近心点)：pos = r·(cosν, sinν, 0)
 *                        vel = √(μ/p)·(−sinν, e + cosν, 0)
 *   再由 R = Rz(Ω)·Rx(i)·Rz(ω) 转到惯性系。
 *
 * 边界情况：径向轨道（h ≈ 0，本类返回 invalid，调用方回退为"不传播"）、
 * 抛物线（能量 ≈ 0）、r ≈ 0 均视为 invalid，不做传播。
 */
public final class TwoBody {

    private static final double EPS = 1.0e-12;

    private TwoBody() {
    }

    /** 二体轨道根数（t0 时刻）。 */
    public static final class Elements {
        /** 是否可用（退化轨道一律 false，调用方回退）。 */
        public final boolean valid;
        /** 是否双曲轨道（e > 1，a < 0）。 */
        public final boolean hyperbolic;
        /** 半长轴（块）；双曲时为负。 */
        public final double a;
        /** 离心率。 */
        public final double e;
        /** 轨道倾角（弧度）。 */
        public final double inclination;
        /** 升交点赤经 Ω（弧度）。 */
        public final double raan;
        /** 近心点幅角 ω（弧度）。 */
        public final double argPeriapsis;
        /** t0 时刻的平近点角 M0（弧度；双曲时即双曲平近点角）。 */
        public final double meanAnomaly0;

        private Elements(boolean valid, boolean hyperbolic, double a, double e,
                         double inclination, double raan, double argPeriapsis, double meanAnomaly0) {
            this.valid = valid;
            this.hyperbolic = hyperbolic;
            this.a = a;
            this.e = e;
            this.inclination = inclination;
            this.raan = raan;
            this.argPeriapsis = argPeriapsis;
            this.meanAnomaly0 = meanAnomaly0;
        }

        /** 不可用根数（退化轨道）。 */
        public static Elements invalid() {
            return new Elements(false, false, 0, 0, 0, 0, 0, 0);
        }

        /** 直接由根数构造（双曲由 e > 1 判定，此时 a 应为负）。 */
        public static Elements of(double a, double e, double inclination, double raan,
                                  double argPeriapsis, double meanAnomaly0) {
            boolean hyp = e > 1.0;
            return new Elements(true, hyp, a, e, inclination, raan, argPeriapsis, meanAnomaly0);
        }

        /** 公转周期（tick）；双曲轨道返回负值（无周期）。 */
        public double periodTicks(double mu) {
            if (!valid || hyperbolic || a <= 0) return -1.0;
            return 2.0 * Math.PI * Math.sqrt(a * a * a / mu);
        }

        /** 近心点 / 远心点距离（块）；双曲时远心点为 -1（无界）。 */
        public double periapsis() {
            return a * (1.0 - e);
        }

        public double apoapsis() {
            return hyperbolic ? -1.0 : a * (1.0 + e);
        }
    }

    // ---------------------------------------------------------------- 状态 → 根数

    /** 由 t0 时刻的位置与速度求轨道根数。退化情形返回 {@link Elements#invalid()}。 */
    public static Elements fromState(double[] r, double[] v, double mu) {
        double rx = r[0], ry = r[1], rz = r[2];
        double vx = v[0], vy = v[1], vz = v[2];
        double rMag = Math.sqrt(rx * rx + ry * ry + rz * rz);
        double v2 = vx * vx + vy * vy + vz * vz;
        if (!(rMag > 1.0e-6) || !(mu > 0.0) || !Double.isFinite(v2)) return Elements.invalid();

        // 角动量 h = r × v
        double hx = ry * vz - rz * vy;
        double hy = rz * vx - rx * vz;
        double hz = rx * vy - ry * vx;
        double hMag = Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (hMag < 1.0e-9) return Elements.invalid(); // 径向轨道：轨道面无定义

        // 偏心率矢量 e = ((v² − μ/r)·r − (r·v)·v) / μ
        double rv = rx * vx + ry * vy + rz * vz;
        double c1 = v2 - mu / rMag;
        double ex = (c1 * rx - rv * vx) / mu;
        double ey = (c1 * ry - rv * vy) / mu;
        double ez = (c1 * rz - rv * vz) / mu;
        double e = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (!Double.isFinite(e)) return Elements.invalid();

        // 比机械能 → 半长轴
        double energy = 0.5 * v2 - mu / rMag;
        if (Math.abs(energy) < 1.0e-15) return Elements.invalid(); // 抛物线（测度零）
        double a = -mu / (2.0 * energy);
        boolean hyperbolic = e > 1.0;

        // 倾角与升交点
        double inc = Math.acos(clamp(hz / hMag, -1.0, 1.0));
        double hxn = hx / hMag, hyn = hy / hMag, hzn = hz / hMag;
        double nx = -hy, ny = hx; // n = ẑ × h
        double nMag = Math.sqrt(nx * nx + ny * ny);
        double raan;
        if (nMag > 1.0e-9) {
            raan = Math.atan2(ny, nx);
            nx /= nMag;
            ny /= nMag;
        } else {
            // 赤道面轨道：节点方向退化为 x 轴
            raan = 0.0;
            nx = 1.0;
            ny = 0.0;
            nMag = 1.0;
        }
        // 轨道面内正交基：n̂（指向升交点）与 m̂ = ĥ × n̂（沿运动方向 90°）
        double mx = hyn * 0.0 - hzn * ny;
        double my = hzn * nx - hxn * 0.0;
        double mz = hxn * ny - hyn * nx;

        double argp;
        double nu;
        double rxn = rx / rMag, ryn = ry / rMag, rzn = rz / rMag;
        if (e > 1.0e-9) {
            double exn = ex / e, eyn = ey / e, ezn = ez / e;
            argp = Math.atan2(exn * mx + eyn * my + ezn * mz, exn * nx + eyn * ny);
            double u = Math.atan2(rxn * mx + ryn * my + rzn * mz, rxn * nx + ryn * ny);
            nu = u - argp;
            nu = Math.atan2(Math.sin(nu), Math.cos(nu)); // 归一化到 (-π, π]
        } else {
            // 近圆轨道：ω 无定义，角度全部由纬度幅角承担
            argp = 0.0;
            nu = Math.atan2(rxn * mx + ryn * my + rzn * mz, rxn * nx + ryn * ny);
        }

        // ν → E/H → M0
        double m0;
        if (hyperbolic) {
            double cosNu = Math.cos(nu), sinNu = Math.sin(nu);
            double denom = 1.0 + e * cosNu;
            if (Math.abs(denom) < 1.0e-12) return Elements.invalid();
            double coshH = (e + cosNu) / denom;
            double sinhH = Math.sqrt(e * e - 1.0) * sinNu / denom;
            double h = Math.log(coshH + sinhH); // asinh，coshH ≥ 1
            if (!Double.isFinite(h)) return Elements.invalid();
            m0 = e * Math.sinh(h) - h;
        } else {
            double cosNu = Math.cos(nu), sinNu = Math.sin(nu);
            double denom = 1.0 + e * cosNu;
            if (Math.abs(denom) < 1.0e-12) return Elements.invalid();
            double cosE = (e + cosNu) / denom;
            double sinE = Math.sqrt(Math.max(0.0, 1.0 - e * e)) * sinNu / denom;
            double ecc = Math.atan2(sinE, cosE);
            m0 = ecc - e * Math.sin(ecc);
        }
        if (!Double.isFinite(m0) || !Double.isFinite(a) || !Double.isFinite(e)) return Elements.invalid();

        return new Elements(true, hyperbolic, a, e, inc, raan, argp, m0);
    }

    // ---------------------------------------------------------------- 根数 → 状态

    /** 由根数在 t0 + dt（tick）时刻求位置 {x,y,z}。传播失败返回 null。 */
    public static double[] positionAt(Elements el, double mu, double dt) {
        double[][] s = stateAt(el, mu, dt);
        return s == null ? null : s[0];
    }

    /** 由根数在 t0 + dt（tick）时刻求速度 {vx,vy,vz}。传播失败返回 null。 */
    public static double[] velocityAt(Elements el, double mu, double dt) {
        double[][] s = stateAt(el, mu, dt);
        return s == null ? null : s[1];
    }

    /**
     * 由根数在 t0 + dt（tick）时刻求 {位置, 速度}（各 3 元 double 数组）。传播失败返回 null。
     */
    public static double[][] stateAt(Elements el, double mu, double dt) {
        if (el == null || !el.valid || !(mu > 0.0)) return null;
        double p = el.a * (1.0 - el.e * el.e);
        if (!(p > 0.0)) return null;

        double nu;
        double r;
        if (el.hyperbolic) {
            double n = Math.sqrt(mu / (-el.a * -el.a * -el.a));
            double m = el.meanAnomaly0 + n * dt;
            double h = solveKeplerHyperbolic(m, el.e);
            if (!Double.isFinite(h)) return null;
            double coshH = Math.cosh(h), sinhH = Math.sinh(h);
            r = el.a * (1.0 - el.e * coshH);
            if (!(r > 0.0)) return null;
            // 双曲真近点角：cos ν = (e − coshH)/(e·coshH − 1)，sin ν = √(e²−1)·sinhH/(e·coshH − 1)
            double denom = el.e * coshH - 1.0;
            double cosNu = (el.e - coshH) / denom;
            double sinNu = (Math.sqrt(el.e * el.e - 1.0) * sinhH) / denom;
            nu = Math.atan2(sinNu, cosNu);
        } else {
            if (!(el.a > 0.0)) return null;
            double n = Math.sqrt(mu / (el.a * el.a * el.a));
            double m = el.meanAnomaly0 + n * dt;
            double ecc = solveKeplerElliptic(m, el.e);
            double cosE = Math.cos(ecc), sinE = Math.sin(ecc);
            r = el.a * (1.0 - el.e * cosE);
            if (!(r > 0.0)) return null;
            double sqrt1me2 = Math.sqrt(Math.max(0.0, 1.0 - el.e * el.e));
            double cosNu = (cosE - el.e) / (1.0 - el.e * cosE);
            double sinNu = (sqrt1me2 * sinE) / (1.0 - el.e * cosE);
            nu = Math.atan2(sinNu, cosNu);
        }

        double cosNu = Math.cos(nu), sinNu = Math.sin(nu);
        double xp = r * cosNu;
        double yp = r * sinNu;
        double k = Math.sqrt(mu / p);
        double vxp = -k * sinNu;
        double vyp = k * (el.e + cosNu);

        // 近焦点系 → 惯性系：R = Rz(Ω)·Rx(i)·Rz(ω)
        double cO = Math.cos(el.raan), sO = Math.sin(el.raan);
        double ci = Math.cos(el.inclination), si = Math.sin(el.inclination);
        double cw = Math.cos(el.argPeriapsis), sw = Math.sin(el.argPeriapsis);
        double r11 = cO * cw - sO * sw * ci;
        double r12 = -cO * sw - sO * cw * ci;
        double r13 = sO * si;
        double r21 = sO * cw + cO * sw * ci;
        double r22 = -sO * sw + cO * cw * ci;
        double r23 = -cO * si;
        double r31 = sw * si;
        double r32 = cw * si;
        double r33 = ci;

        double[] pos = new double[]{
                r11 * xp + r12 * yp,
                r21 * xp + r22 * yp,
                r31 * xp + r32 * yp
        };
        double[] vel = new double[]{
                r11 * vxp + r12 * vyp,
                r21 * vxp + r22 * vyp,
                r31 * vxp + r32 * vyp
        };
        return new double[][]{pos, vel};
    }

    // ---------------------------------------------------------------- 开普勒方程

    /** 解椭圆开普勒方程 E − e·sinE = M（e < 1）。M 任意大，内部归一化。 */
    public static double solveKeplerElliptic(double m, double e) {
        if (e < 1.0e-12) return m;
        double M = ((m % (2.0 * Math.PI)) + 2.0 * Math.PI) % (2.0 * Math.PI);
        double E = (e < 0.8) ? M + e * Math.sin(M) : Math.PI;
        for (int i = 0; i < 60; i++) {
            double f = E - e * Math.sin(E) - M;
            double fp = 1.0 - e * Math.cos(E);
            double d = f / fp;
            E -= d;
            if (Math.abs(d) < 1.0e-14) break;
        }
        return E;
    }

    /** 解双曲开普勒方程 e·sinhH − H = M（e > 1）。 */
    public static double solveKeplerHyperbolic(double m, double e) {
        if (!(e > 1.0)) return Double.NaN;
        double h = Math.log(2.0 * Math.abs(m) / e + 1.8); // asinh(m/e) 的廉价近似
        if (m < 0) h = -h;
        for (int i = 0; i < 100; i++) {
            double f = e * Math.sinh(h) - h - m;
            double fp = e * Math.cosh(h) - 1.0;
            if (Math.abs(fp) < 1.0e-14) break;
            double d = f / fp;
            h -= d;
            if (Math.abs(d) < 1.0e-13) break;
        }
        return h;
    }

    private static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : (x > hi ? hi : x);
    }
}
