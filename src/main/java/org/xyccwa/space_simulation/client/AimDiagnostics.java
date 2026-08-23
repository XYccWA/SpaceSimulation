// AimDiagnostics.java —— 准星/拾取对齐诊断（系统属性门控，不影响正常运行）
//
// 用法：runClient 加 -Dspacesim.aimdiag=true。每 20 tick 打印一次：
//   1. camera 前向（画面中心方向） vs 玩家 getViewVector（拾取射线方向）的夹角与差值
//   2. camera 位置（画面眼位） vs 玩家 getEyePosition（拾取起点）的空间差值
//   3. 同起点下分别沿两个方向打 blockInteractionRange 长射线，命中的方块坐标
// 用于一锤定音判断"准星对准 ≠ 实际瞄准"的几何根因（方向不一致 or 起点分离 or 两者）。
package org.xyccwa.space_simulation.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.api.EntityRotation;

@EventBusSubscriber(value = Dist.CLIENT, modid = SpaceSimulation.MOD_ID)
public class AimDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger("AimDiag");
    private static final String GATE = "spacesim.aimdiag";
    private static boolean enabled = false;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!enabled) {
            enabled = Boolean.getBoolean(GATE);
            if (!enabled) {
                return;
            }
            LOGGER.info("[AimDiag] enabled by -D{}", GATE);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.player instanceof EntityRotation rot) || !rot.hasOrientation()) {
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        Entity player = mc.player;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return;
        }

        // 相机前向（画面中心像素方向）
        Vector3f camFwd = camera.getLookVector();
        Vec3 camDir = new Vec3(camFwd.x(), camFwd.y(), camFwd.z());
        Vec3 camPos = camera.getPosition();

        // 拾取射线：起点 = 原版 getEyePosition(世界 Y 眼位)，方向 = getViewVector(四元数朝向)
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 pickDir = player.getViewVector(1.0F);

        // 欧拉角重建方向（原版 calculateViewVector，直接用 yRot/xRot）——
        // 验证"是否仍在使用原版视角转动"：若 pickup 实际命中用的是它，则与四元数 pickDir 不一致。
        Vec3 eulerDir = calculateViewVector(player.getXRot(), player.getYRot());

        // 相机前向作为方向、相机位置作为起点的"准星射线"
        BlockPos camHit = ray(player, camPos, camDir);
        // 拾取射线（与 GameRenderer.pick 完全一致）
        BlockPos pickHit = player.pick(mc.player.blockInteractionRange(), 1.0F, false) instanceof BlockHitResult b
                ? b.getBlockPos() : null;
        // 欧拉角重建方向同起点的命中方块（验证是否更接近准星方块）
        BlockPos eulerHit = ray(player, eyePos, eulerDir);

        // 夹角（度）与起点距离
        double cosPick = camDir.dot(pickDir) / (camDir.length() * pickDir.length());
        double anglePick = Math.toDegrees(Math.acos(Mth.clamp(cosPick, -1.0, 1.0)));
        double cosEuler = camDir.dot(eulerDir) / (camDir.length() * eulerDir.length());
        double angleEuler = Math.toDegrees(Math.acos(Mth.clamp(cosEuler, -1.0, 1.0)));
        double startDist = camPos.distanceTo(eyePos);

        LOGGER.info("[AimDiag] angle(camFwd,getViewVector[quat])={}°  angle(camFwd,eulerRebuild)={}°  d(camPos vs getEyePos)={}",
                fmt(anglePick), fmt(angleEuler), fmt(startDist));
        LOGGER.info("[AimDiag] camDir=({}) pickDir=({}) eulerDir=({})",
                fmt(camDir), fmt(pickDir), fmt(eulerDir));
        LOGGER.info("[AimDiag] camPos=({}) eyePos=({})", fmt(camPos), fmt(eyePos));
        LOGGER.info("[AimDiag] camRayHit={} pickHit={} eulerHit={}",
                camHit, pickHit, eulerHit);
    }

    private static BlockPos ray(Entity entity, Vec3 from, Vec3 dir) {
        double range = 8.0;
        Vec3 to = from.add(dir.scale(range));
        HitResult hr = entity.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
        return hr.getType() == HitResult.Type.MISS ? null : BlockPos.containing(hr.getLocation());
    }

    /** 原版 Entity.calculateViewVector 的纯重算（绕开可能被覆盖的 getViewVector）。 */
    private static Vec3 calculateViewVector(float xRot, float yRot) {
        float f = xRot * (float) (Math.PI / 180.0);
        float f1 = -yRot * (float) (Math.PI / 180.0);
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3(f3 * f4, -f5, f2 * f4);
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }

    private static String fmt(Vec3 v) {
        return String.format("%.3f, %.3f, %.3f", v.x, v.y, v.z);
    }
}
