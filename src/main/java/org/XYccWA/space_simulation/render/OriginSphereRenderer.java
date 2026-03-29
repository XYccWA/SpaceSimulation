// src/main/java/org/XYccWA/space_simulation/client/OriginSphereRenderer.java
package org.XYccWA.space_simulation.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID, value = Dist.CLIENT)
public class OriginSphereRenderer {
    private static final int SPHERE_RADIUS = 50000;
    private static final ResourceLocation SPHERE_TEXTURE =
            new ResourceLocation(SpaceSimulationMod.MOD_ID, "textures/entity/sphere.png");

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
                SpaceSimulationMod.LOGGER.error("Failed to get render buffers", e);
                return;
            }

            if (bufferSource == null) {
                SpaceSimulationMod.LOGGER.warn("Buffer source is null");
                return;
            }

            // 计算玩家到原点的向量
            Vec3 playerToOrigin = new Vec3(0, 0, 0).subtract(cameraPos);

            // 使用相对位置渲染
            poseStack.pushPose();
            poseStack.translate(playerToOrigin.x, playerToOrigin.y, playerToOrigin.z);

            // 使用更合适的渲染类型
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(SPHERE_TEXTURE));
            // 渲染带纹理的球面
            renderSphere(poseStack, consumer, SPHERE_RADIUS);

            poseStack.popPose();
        }
    }

    private static void renderSphere(PoseStack poseStack, VertexConsumer consumer, int radius) {
        int latitudeLines = 32; // 从100降低到32
        int longitudeLines = 64; // 从100降低到64

        for (int i = 0; i < latitudeLines; i++) {
            double theta1 = Math.PI * i / latitudeLines;
            double theta2 = Math.PI * (i + 1) / latitudeLines;

            for (int j = 0; j < longitudeLines; j++) {
                double phi1 = 2 * Math.PI * j / longitudeLines;
                double phi2 = 2 * Math.PI * (j + 1) / longitudeLines;

                // 计算顶点坐标
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

                // 计算UV坐标
                float u1 = (float) j / longitudeLines;
                float v1 = (float) i / latitudeLines;
                float u2 = (float) (j + 1) / longitudeLines;
                float v2 = (float) (i + 1) / latitudeLines;

                // 第一个三角形 (1-2-4)
                addVertex(consumer, poseStack, x1, y1, z1, u1, v1);
                addVertex(consumer, poseStack, x2, y2, z2, u2, v1);
                addVertex(consumer, poseStack, x4, y4, z4, u1, v2);

                // 第二个三角形 (2-3-4)
                addVertex(consumer, poseStack, x2, y2, z2, u2, v1);
                addVertex(consumer, poseStack, x3, y3, z3, u2, v2);
                addVertex(consumer, poseStack, x4, y4, z4, u1, v2);
            }
        }
    }

    private static void addVertex(VertexConsumer consumer, PoseStack poseStack,
                                  double x, double y, double z, float u, float v) {
        Vec3 normal = new Vec3(x, y, z).normalize();

        // 使用正确的顶点格式
        consumer.vertex(poseStack.last().pose(), (float) x, (float) y, (float) z)
                .color(1.0F, 1.0F, 1.0F, 1.0F)
                .uv(u, v)
                .overlayCoords(0, 10)
                .uv2(0)
                .normal((float) normal.x, (float) normal.y, (float) normal.z)
                .endVertex();
    }
}
