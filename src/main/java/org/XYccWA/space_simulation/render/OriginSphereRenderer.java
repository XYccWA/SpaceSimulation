// src/main/java/org/XYccWA/space_simulation/client/OriginSphereRenderer.java
package org.XYccWA.space_simulation.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID, value = Dist.CLIENT)
public class OriginSphereRenderer {
    private static final int SPHERE_RADIUS = 100;
    private static final int BRIGHTNESS = 15;


    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            PoseStack poseStack = event.getPoseStack();
            LevelRenderer levelRenderer = event.getLevelRenderer();
            Vec3 cameraPos = event.getCamera().getPosition();

            // 使用反射获取 renderBuffers
            MultiBufferSource.BufferSource bufferSource = null;
            try {
                Field renderBuffersField = LevelRenderer.class.getDeclaredField("renderBuffers");
                renderBuffersField.setAccessible(true);
                Object renderBuffers = renderBuffersField.get(levelRenderer);

                // 获取 bufferSource
                Field bufferSourceField = renderBuffers.getClass().getDeclaredField("bufferSource");
                bufferSourceField.setAccessible(true);
                bufferSource = (MultiBufferSource.BufferSource) bufferSourceField.get(renderBuffers);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            if (bufferSource == null) {
                return;
            }

            // 计算玩家到原点的向量
            Vec3 playerToOrigin = new Vec3(0, 0, 0).subtract(cameraPos);

            // 使用相对位置渲染
            poseStack.pushPose();
            poseStack.translate(playerToOrigin.x, playerToOrigin.y, playerToOrigin.z);

            VertexConsumer consumer = bufferSource.getBuffer(RenderType.endPortal());
            // 渲染白色完整球面
            renderSphere(poseStack, consumer, SPHERE_RADIUS, BRIGHTNESS);

            poseStack.popPose();
        }
    }

    private static void renderSphere(PoseStack poseStack, VertexConsumer consumer, int radius, int brightness) {
        int latitudeLines = 100;
        int longitudeLines = 100;

        for (int i = 0; i < latitudeLines; i++) {
            double theta1 = Math.PI * i / latitudeLines;
            double theta2 = Math.PI * (i + 1) / latitudeLines;

            for (int j = 0; j < longitudeLines; j++) {
                double phi1 = 2 * Math.PI * j / longitudeLines;
                double phi2 = 2 * Math.PI * (j + 1) / longitudeLines;

                // 使用double计算坐标
                double x1 = radius * Math.sin(theta1) * Math.cos(phi1);
                double y1 = radius * Math.cos(theta1);
                double z1 = radius * Math.sin(theta1) * Math.sin(phi1);

                double x2 = radius * Math.sin(theta1) * Math.cos(phi2);
                double y2 = radius * Math.cos(theta1);
                double z2 = radius * Math.sin(theta1) * Math.sin(phi2);

                double x3 = radius * Math.sin(theta2) * Math.cos(phi2);
                double y3 = radius * Math.cos(theta2);
                double z3 = radius * Math.sin(theta2) * Math.sin(phi2);

                double x4 = radius * Math.sin(theta2) * Math.cos(phi1);
                double y4 = radius * Math.cos(theta2);
                double z4 = radius * Math.sin(theta2) * Math.sin(phi1);

                // 计算法线
                Vec3 normal1 = new Vec3(x1, y1, z1).normalize();
                Vec3 normal2 = new Vec3(x2, y2, z2).normalize();
                Vec3 normal3 = new Vec3(x3, y3, z3).normalize();
                Vec3 normal4 = new Vec3(x4, y4, z4).normalize();

                // 第一个三角形 (1-2-4)
                consumer.vertex(poseStack.last().pose(), (float) x1, (float) y1, (float) z1)
                        .uv(0, 0)
                        .color(255, 255, 255, 255)
                        .normal((float) normal1.x, (float) normal1.y, (float) normal1.z)
                        .endVertex();

                consumer.vertex(poseStack.last().pose(), (float) x2, (float) y2, (float) z2)
                        .uv(1, 0)
                        .color(255, 255, 255, 255)
                        .normal((float) normal2.x, (float) normal2.y, (float) normal2.z)
                        .endVertex();

                consumer.vertex(poseStack.last().pose(), (float) x4, (float) y4, (float) z4)
                        .uv(0, 1)
                        .color(255, 255, 255, 255)
                        .normal((float) normal4.x, (float) normal4.y, (float) normal4.z)
                        .endVertex();

                // 第二个三角形 (2-3-4)
                consumer.vertex(poseStack.last().pose(), (float) x2, (float) y2, (float) z2)
                        .uv(1, 0)
                        .color(255, 255, 255, 255)
                        .normal((float) normal2.x, (float) normal2.y, (float) normal2.z)
                        .endVertex();

                consumer.vertex(poseStack.last().pose(), (float) x3, (float) y3, (float) z3)
                        .uv(1, 1)
                        .color(255, 255, 255, 255)
                        .normal((float) normal3.x, (float) normal3.y, (float) normal3.z)
                        .endVertex();

                consumer.vertex(poseStack.last().pose(), (float) x4, (float) y4, (float) z4)
                        .uv(0, 1)
                        .color(255, 255, 255, 255)
                        .normal((float) normal4.x, (float) normal4.y, (float) normal4.z)
                        .endVertex();
            }
        }
    }
}
