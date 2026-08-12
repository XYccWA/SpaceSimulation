// ClientTickHandler.java
package org.xyccwa.space_simulation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.api.EntityRotation;
import org.xyccwa.space_simulation.network.PlayerControlPayload;
import org.xyccwa.space_simulation.player.PlayerAcceleration;
import org.xyccwa.space_simulation.util.FlightPhysics;

@EventBusSubscriber(value = Dist.CLIENT, modid = SpaceSimulation.MOD_ID)
public class ClientTickHandler {

    /** 上一帧的 partialTick，用于按帧时间差缩放滚转速度（帧率无关）。 */
    private static float lastPartialTick = 0.0F;
    /** 上一帧的时间戳（nanoTime），用于视角平滑的帧间隔。 */
    private static long smoothLastNs = 0;
    /** 视角平滑时间常数（秒）。越小响应越快越"生硬"，越大越平滑但滞后越明显。 */
    private static final float VIEW_SMOOTHING_TAU = 0.04F;
    /** 原版按键重绑定只执行一次。 */
    private static boolean keysConfigured = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.player instanceof EntityRotation rot)) {
            return;
        }

        configureKeys(mc);

        // 先更新运动数据（本地权威速度差分），再随控制包上报服务器判伤
        PlayerAcceleration.updatePlayer(mc.player);
        PacketDistributor.sendToServer(new PlayerControlPayload(rot.getOrientation(), rot.getMoveMask(),
                (float) PlayerAcceleration.getSmoothedAcceleration(mc.player),
                (float) PlayerAcceleration.getImpactDeltaV(mc.player)));
    }

    /**
     * 每帧：先做视角平滑（显示朝向指数缓动追向实际朝向，帧率无关），再应用 Q/E 滚转。
     * 相机/模型读取显示朝向（见 CameraMixin/LivingEntityRendererMixin），
     * 物理方向与准星仍读实际朝向，保持零滞后响应。
     */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player instanceof EntityRotation rot) || !rot.hasOrientation()) {
            lastPartialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            return;
        }

        // 每帧的滚转增量基准（partialTick 差，帧率无关）
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float delta = (pt - lastPartialTick + 1.0F) % 1.0F;
        lastPartialTick = pt;

        // 视角平滑：按真实帧间隔做指数缓动。alpha = 1 - exp(-dt/tau)，
        // 60fps 时约 0.49、23fps 时约 0.82，帧率无关——低帧率也能把逐帧大突变铺平成连续滑动。
        long now = System.nanoTime();
        float frameSeconds = (smoothLastNs == 0) ? 0.0167F
                : Math.min(Math.max((float) ((now - smoothLastNs) / 1e9), 0.001F), 0.25F);
        smoothLastNs = now;
        float alpha = 1.0F - (float) Math.exp(-frameSeconds / VIEW_SMOOTHING_TAU);
        rot.smoothOrientation(alpha);

        // 滚转：Q 左滚、E 右滚，按帧时间差缩放速度
        float roll = 0.0F;
        if (KeyMappingHandler.ROLL_LEFT.isDown()) {
            roll -= FlightPhysics.ROLL_SPEED;
        }
        if (KeyMappingHandler.ROLL_RIGHT.isDown()) {
            roll += FlightPhysics.ROLL_SPEED;
        }
        if (roll != 0.0F) {
            Quaternionf q = rot.getOrientation();
            q.mul(new Quaternionf().rotationZ(roll * delta));
            rot.setOrientation(q);
        }
    }

    /**
     * 首次进入游戏后重绑定原版按键：丢物品 Q→DEL、打开背包 E→B。
     * 仅当仍是原版默认键时改，尊重玩家在控制设置里的自定义。
     */
    private static void configureKeys(Minecraft mc) {
        if (keysConfigured) {
            return;
        }
        keysConfigured = true;
        KeyMapping drop = mc.options.keyDrop;
        KeyMapping inventory = mc.options.keyInventory;
        if (drop.getKey().getValue() == GLFW.GLFW_KEY_Q) {
            drop.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_DELETE));
        }
        if (inventory.getKey().getValue() == GLFW.GLFW_KEY_E) {
            inventory.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));
        }
    }
}
