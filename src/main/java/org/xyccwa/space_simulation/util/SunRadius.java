package org.xyccwa.space_simulation.util;

import net.minecraft.server.MinecraftServer;

import java.util.Random;

/**
 * 太阳半径的唯一定义处。
 *
 * 半径由世界种子确定性派生（{@link #MIN_RADIUS} ~ {@link #MAX_RADIUS}），
 * 服务端判定（高温区、表面处死）与客户端渲染（WorldSphereRenderer）共用同一公式，
 * 保证"看到的太阳表面"就是"判定的太阳表面"。客户端在多人游戏中拿不到世界种子，
 * 由服务端在玩家登录时通过网络包下发实际半径（见 SunRadiusPayload）。
 */
public final class SunRadius {

    /** 太阳半径下限（格） */
    public static final double MIN_RADIUS = 50_000.0;
    /** 太阳半径上限（格） */
    public static final double MAX_RADIUS = 100_000.0;

    /** 服务端半径缓存：种子不变则复用（同一世界每 tick 都会查询） */
    private static long cachedSeed = Long.MIN_VALUE;
    private static double cachedRadius = MIN_RADIUS;

    private SunRadius() {
    }

    /** 由世界种子派生太阳半径（客户端与服务端必须使用同一实现）。 */
    public static double forSeed(long seed) {
        Random rnd = new Random(seed);
        return MIN_RADIUS + rnd.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
    }

    /** 服务端当前世界的太阳半径（以主世界种子为准，太阳球心在世界原点）。 */
    public static double forServer(MinecraftServer server) {
        if (server == null) return MIN_RADIUS;
        long seed = server.overworld().getSeed();
        if (seed != cachedSeed) {
            cachedSeed = seed;
            cachedRadius = forSeed(seed);
        }
        return cachedRadius;
    }
}
