package org.xyccwa.space_simulation.orbital;

/**
 * 太阳引力场（中心天体在世界原点，球对称平方反比）。
 *
 * 唯一参数 μ（块³/tick²）由配置给出；客户端在多人游戏中拿不到服务器配置，
 * 由服务端登录时通过网络包下发（见 OrbitalParamsPayload），保证两端积分完全一致。
 *
 * 纯数值、无 Minecraft 依赖：客户端预测与服务端权威模拟共用同一实现。
 * μ ≤ 0 视为关闭引力（回到旧的"无引力牛顿自由飞行"）。
 */
public final class Gravity {

    /** 当前生效的引力参数（0 = 关闭）。服务端来自配置，客户端来自服务端下发。 */
    private static volatile double mu = 0.0;
    /** 是否已经解析出可用值。 */
    private static volatile boolean resolved = false;
    /** 是否由服务端网络包显式覆盖（客户端）：覆盖后不再被本地配置重载改回去。 */
    private static volatile boolean overriddenByServer = false;
    /** 本地配置回退源（服务端/单机用；客户端在收到包之前也用它兜底）。 */
    private static volatile java.util.function.DoubleSupplier fallback;

    private Gravity() {
    }

    /**
     * 注入本地配置读取器。STARTUP 配置在 mod 构造期就已加载，但 ModConfigEvent.Loading
     * 会在监听器注册之前触发（实测），所以这里做惰性解析：第一次读 mu() 时才取配置值。
     */
    public static void setFallback(java.util.function.DoubleSupplier supplier) {
        fallback = supplier;
        if (!overriddenByServer) {
            resolved = false; // 配置重载后重新解析
        }
    }

    /** 服务端下发的权威值（客户端用）。 */
    public static void setMu(double value) {
        mu = sanitize(value);
        resolved = true;
        overriddenByServer = true;
    }

    private static double sanitize(double value) {
        return (Double.isFinite(value) && value > 0.0) ? value : 0.0;
    }

    public static double mu() {
        if (!resolved) {
            java.util.function.DoubleSupplier source = fallback;
            if (source != null) {
                mu = sanitize(source.getAsDouble());
                resolved = true;
            }
        }
        return mu;
    }

    public static boolean enabled() {
        return mu() > 0.0;
    }

    /**
     * 位置 (x,y,z) 处的引力加速度，写入 out[0..2]（块/tick²）。
     * 原点附近做下限保护（r ≥ 1 块），避免 r → 0 时数值爆炸。
     */
    public static void acceleration(double x, double y, double z, double[] out) {
        double m = mu();
        if (m <= 0.0) {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            return;
        }
        double r2 = x * x + y * y + z * z;
        double r = Math.sqrt(r2);
        if (r < 1.0) r = 1.0;
        double k = -m / (r2 * r);
        out[0] = k * x;
        out[1] = k * y;
        out[2] = k * z;
    }
}
