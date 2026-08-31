package org.xyccwa.space_simulation.asteroid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * 小行星近邻加载服务（无实体化）—— 每 tick 自动驱动加载器。
 *
 * 由服务端 tick 事件驱动（SpaceSimulation 主类注册本类的 tick 监听）：
 *   每个 tick 取主世界第一个在线玩家作为锚点，调用 AsteroidProximityLoader.update(...)：
 *     预加载索引按分帧持续推进（位移超阈值或定期到期时整轮重建，其余时间索引保持可用）；
 *     强加载每 tick 从索引精确检索。全程不阻塞主线程（每 tick 只做一帧 ~千级候选精测）。
 */
public final class AsteroidProximityService {

    private static volatile AsteroidProximityLoader loader;

    private AsteroidProximityService() {}

    /** 共享加载器实例（懒创建：预加载 10000 / 强加载 2000 / 定期检索间隔 600 tick）。 */
    public static AsteroidProximityLoader loader() {
        AsteroidProximityLoader l = loader;
        if (l == null) {
            synchronized (AsteroidProximityService.class) {
                l = loader;
                if (l == null) {
                    l = new AsteroidProximityLoader(AsteroidUniverseSource.fromConfig(),
                            10_000.0, 2_000.0, 600);
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