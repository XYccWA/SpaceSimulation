package org.XYccWA.space_simulation.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID)
public class PlayerPositionTracker {

    private static final java.util.Map<Player, MovementData> playerDataMap = new java.util.HashMap<>();

    // 平滑因子，0-1之间，值越小平滑效果越强但延迟越大
    private static final double SMOOTHING_FACTOR = 0.3;

    // 最小速度阈值，低于此值视为静止
    private static final double MIN_VELOCITY_THRESHOLD = 0.001;

    private static class MovementData {
        Vec3 lastVelocity;
        Vec3 smoothedVelocity;
        double currentAcceleration;

        MovementData(Vec3 velocity) {
            this.lastVelocity = velocity;
            this.smoothedVelocity = velocity;
        }

        // 使用指数平滑处理速度向量
        Vec3 smoothVelocity(Vec3 newVelocity) {
            smoothedVelocity = new Vec3(
                    SMOOTHING_FACTOR * newVelocity.x + (1 - SMOOTHING_FACTOR) * smoothedVelocity.x,
                    SMOOTHING_FACTOR * newVelocity.y + (1 - SMOOTHING_FACTOR) * smoothedVelocity.y,
                    SMOOTHING_FACTOR * newVelocity.z + (1 - SMOOTHING_FACTOR) * smoothedVelocity.z
            );
            return smoothedVelocity;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player.level().isClientSide()) {
            return;
        }

        Player player = event.player;
        Vec3 currentVelocity = player.getDeltaMovement();

        MovementData data = playerDataMap.get(player);
        if (data == null) {
            playerDataMap.put(player, new MovementData(currentVelocity));
            return;
        }

        // 平滑处理当前速度
        Vec3 smoothedVelocity = data.smoothVelocity(currentVelocity);

        // 计算速度变化向量（格/tick）
        Vec3 velocityChange = smoothedVelocity.subtract(data.lastVelocity);

        // 计算加速度向量（格/秒²）
        Vec3 acceleration = velocityChange.scale(20 * 20);

        // 计算合加速度（加速度的模长）
        double totalAcceleration = acceleration.length();

        // 存储数据
        data.currentAcceleration = totalAcceleration;
        data.lastVelocity = smoothedVelocity;
    }

    public static double getPlayerSmoothedSpeed(Player player) {
        MovementData data = playerDataMap.get(player);
        return data != null ? data.smoothedVelocity.length() * 20 : 0;
    }

    public static double getPlayerAcceleration(Player player) {
        MovementData data = playerDataMap.get(player);
        return data != null ? data.currentAcceleration : 0;
    }
}
