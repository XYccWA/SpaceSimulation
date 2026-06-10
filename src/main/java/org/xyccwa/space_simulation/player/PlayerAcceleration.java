// PlayerAcceleration.java - 修改版本
package org.xyccwa.space_simulation.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerAcceleration {
    // 使用 ConcurrentHashMap 保证线程安全
    private static final Map<Player, Vec3> lastPositionMap = new ConcurrentHashMap<>();
    private static final Map<Player, Vec3> lastVelocityMap = new ConcurrentHashMap<>();
    private static final Map<Player, Vec3> velocityMap = new ConcurrentHashMap<>();
    private static final Map<Player, Vec3> accelerationMap = new ConcurrentHashMap<>();

    /**
     * 每 tick 调用此方法更新玩家运动数据
     * 需要在 ClientTickEvent 或 PlayerTickEvent 中调用
     */
    public static void updatePlayer(Player player) {
        if (player == null) return;

        Vec3 currentPos = player.position();

        // 计算速度
        Vec3 velocity = Vec3.ZERO;
        if (lastPositionMap.containsKey(player)) {
            velocity = currentPos.subtract(lastPositionMap.get(player));
        }
        lastPositionMap.put(player, currentPos);

        // 计算加速度
        Vec3 acceleration = Vec3.ZERO;
        if (lastVelocityMap.containsKey(player)) {
            acceleration = velocity.subtract(lastVelocityMap.get(player));
        }
        lastVelocityMap.put(player, velocity);

        // 存储当前值
        velocityMap.put(player, velocity);
        accelerationMap.put(player, acceleration);
    }

    public static Vec3 getCurrentVelocity(Player player) {
        return velocityMap.getOrDefault(player, Vec3.ZERO);
    }

    public static Vec3 getCurrentAcceleration(Player player) {
        return accelerationMap.getOrDefault(player, Vec3.ZERO);
    }

    public static void removePlayer(Player player) {
        lastPositionMap.remove(player);
        lastVelocityMap.remove(player);
        velocityMap.remove(player);
        accelerationMap.remove(player);
    }
}