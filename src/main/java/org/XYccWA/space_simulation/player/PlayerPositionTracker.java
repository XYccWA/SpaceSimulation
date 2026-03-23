package org.XYccWA.space_simulation.player;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID)
public class PlayerPositionTracker {

    // 存储玩家上一tick的位置和速度
    private static final java.util.Map<Player, MovementData> playerDataMap = new java.util.HashMap<>();

    // 速度平滑窗口大小（存储最近N个tick的速度）
    private static final int SMOOTHING_WINDOW = 5;

    // 内部类存储运动数据
    private static class MovementData {
        net.minecraft.world.phys.Vec3 lastPosition;
        double lastSpeedPerSecond;
        // 使用循环队列存储最近的速度值
        private final double[] speedHistory;
        private int historyIndex;
        private boolean historyFilled;

        MovementData(net.minecraft.world.phys.Vec3 position, double speed) {
            this.lastPosition = position;
            this.lastSpeedPerSecond = speed;
            this.speedHistory = new double[SMOOTHING_WINDOW];
            this.historyIndex = 0;
            this.historyFilled = false;
        }

        // 添加速度到历史记录并返回平滑后的速度
        double addSpeedAndGetSmoothed(double speed) {
            speedHistory[historyIndex] = speed;
            historyIndex = (historyIndex + 1) % SMOOTHING_WINDOW;

            if (historyIndex == 0) {
                historyFilled = true;
            }

            // 计算平均值
            double sum = 0;
            int count = historyFilled ? SMOOTHING_WINDOW : historyIndex;
            for (int i = 0; i < count; i++) {
                sum += speedHistory[i];
            }
            return sum / count;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 只在服务器端处理，且只在tick开始时处理一次
        if (event.phase != TickEvent.Phase.START || event.player.level().isClientSide()) {
            return;
        }

        Player player = event.player;

        // 获取当前坐标
        net.minecraft.world.phys.Vec3 currentPosition = player.position();

        // 获取上一tick数据
        MovementData data = playerDataMap.get(player);

        if (data == null) {
            // 首次记录位置，不计算速度和加速度
            playerDataMap.put(player, new MovementData(currentPosition, 0));
            return;
        }

        // 计算位移向量
        net.minecraft.world.phys.Vec3 displacement = currentPosition.subtract(data.lastPosition);

        // 计算瞬时速度（格/tick）
        double speed = displacement.length();

        // 转换为格/秒（1秒=20tick）
        double speedPerSecond = speed * 20;

        // 使用滑动平均进行速度平滑
        double smoothedSpeed = data.addSpeedAndGetSmoothed(speedPerSecond);

        // 计算速度变化（格/秒）
        double velocityChange = smoothedSpeed - data.lastSpeedPerSecond;

        // 计算加速度（格/秒²）
        double acceleration = velocityChange * 20;

        // 发送坐标、速度和加速度到聊天栏
        player.sendSystemMessage(Component.literal(String.format(
                "位置: X: %.2f, Y: %.2f, Z: %.2f | 速度: %.2f 格/秒 | 加速度: %.2f 格/秒²",
                currentPosition.x, currentPosition.y, currentPosition.z,
                smoothedSpeed,
                acceleration
        )));

        // 更新数据
        data.lastPosition = currentPosition;
        data.lastSpeedPerSecond = smoothedSpeed;
    }
}
