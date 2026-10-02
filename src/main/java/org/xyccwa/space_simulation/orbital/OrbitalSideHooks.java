package org.xyccwa.space_simulation.orbital;

import net.minecraft.world.entity.player.Player;
import org.xyccwa.space_simulation.api.EntityRotation;

/**
 * 客户端/服务端分侧的间接调用点。
 *
 * 为什么需要：{@code PlayerMixin} 是通用（双端）mixin，其字节码会随 {@code Player} 一起
 * 在专用服务端被加载/校验；如果它直接引用只存在于客户端的类（Minecraft/LocalPlayer），
 * 专用服务端会因为解析不到这些类而报错。这里用一个通用接口做间接层：实现由客户端在
 * 类加载时注入，服务端永远为 null（那些方法也就永远不会被执行到）。
 */
public final class OrbitalSideHooks {

    /** 客户端本地预测的每 tick 入口。 */
    public interface ClientTicker {
        void tick(Player player, EntityRotation rotation, int moveMask);
    }

    private static volatile ClientTicker clientTicker;
    private static boolean warnedNull = false;

    private OrbitalSideHooks() {
    }

    /** 由客户端类在静态初始化时注入。 */
    public static void setClientTicker(ClientTicker ticker) {
        clientTicker = ticker;
    }

    public static boolean hasClientTicker() {
        return clientTicker != null;
    }

    public static void tickClient(Player player, EntityRotation rotation, int moveMask) {
        ClientTicker ticker = clientTicker;
        if (ticker == null) {
            if (!warnedNull) {
                warnedNull = true;
                org.xyccwa.space_simulation.SpaceSimulation.LOGGER.warn(
                        "[轨道调试] 客户端预测钩子为空：类加载顺序问题，本地预测未运行");
            }
            return;
        }
        ticker.tick(player, rotation, moveMask);
    }
}
