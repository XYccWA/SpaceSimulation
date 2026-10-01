package org.xyccwa.space_simulation.asteroid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.xyccwa.space_simulation.asteroid.data.AsteroidCatalog;

import java.util.List;

/**
 * 小行星近邻加载服务（无实体化）—— 每 tick 自动驱动加载器。
 *
 * 宇宙来源：数据包（asteroid_belt / asteroid_type）经 {@link AsteroidProximityService#install}
 * 原子安装；数据包尚未加载或全部无效时使用 {@link AsteroidUniverseSource#fallback()} 内置默认单带。
 *
 * 由服务端 tick 事件驱动（SpaceSimulation 主类注册本类的 tick 监听）：
 *   每个 tick 取主世界第一个在线玩家作为锚点，调用 AsteroidProximityLoader.update(...)：
 *     预加载索引仅在位移超阈值时整轮分帧重建，其余时间索引可用；
 *     预载区间按间隔平移刷新；强加载每 tick 从索引精确检索。
 *     全程不阻塞主线程（每 tick 只做一帧 ~千级候选精测）。
 */
public final class AsteroidProximityService {

    /** 预加载半径（块）：远大于玩家可跑出范围，索引重建阈值 = 该值 × 0.125。 */
    public static final double PRELOAD_RADIUS = 50_000.0;
    /** 强加载半径（块），必须小于预加载半径。 */
    public static final double STRONG_RADIUS = 2_000.0;
    /** 预载区间平移刷新间隔（tick）。 */
    public static final int PRELOAD_INTERVAL_TICKS = 20;

    private static volatile AsteroidCatalog catalog;
    private static volatile AsteroidProximityLoader loader;

    private AsteroidProximityService() {}

    /** 当前宇宙（数据包未加载时返回内置默认单带宇宙）。 */
    public static AsteroidUniverse universe() {
        return catalog().universe;
    }

    /** 当前数据包 catalog（数据包未加载时返回内置回退 catalog）。 */
    public static AsteroidCatalog catalog() {
        AsteroidCatalog c = catalog;
        if (c == null) {
            synchronized (AsteroidProximityService.class) {
                c = catalog;
                if (c == null) {
                    c = new AsteroidCatalog(AsteroidUniverseSource.fallback(),
                            List.of("数据包尚未加载，使用内置默认单带"), 0, 0, true);
                    catalog = c;
                }
            }
        }
        return c;
    }

    /** 数据包重载后安装新宇宙：原子替换 catalog 并按新宇宙重建加载器（旧索引整体丢弃）。 */
    public static void install(AsteroidCatalog newCatalog) {
        synchronized (AsteroidProximityService.class) {
            catalog = newCatalog;
            loader = new AsteroidProximityLoader(newCatalog.universe,
                    PRELOAD_RADIUS, STRONG_RADIUS, PRELOAD_INTERVAL_TICKS);
        }
    }

    /** 共享加载器实例（懒创建；install 后被替换为新宇宙的加载器）。 */
    public static AsteroidProximityLoader loader() {
        AsteroidProximityLoader l = loader;
        if (l == null) {
            synchronized (AsteroidProximityService.class) {
                l = loader;
                if (l == null) {
                    l = new AsteroidProximityLoader(universe(),
                            PRELOAD_RADIUS, STRONG_RADIUS, PRELOAD_INTERVAL_TICKS);
                    loader = l;
                }
            }
        }
        return l;
    }

    /** 服务端每 tick 驱动（ServerTickEvent.Post）。无在线玩家时跳过（索引保留）。 */
    public static void tick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level == null) return;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        ServerPlayer p = players.get(0);
        loader().update(p.getX(), p.getY(), p.getZ(), level.getGameTime());
    }
}
