package org.xyccwa.space_simulation.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.xyccwa.space_simulation.SpaceSimulation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 世界球体渲染器:在世界原点 (0,0,0) 渲染半径 25000 格的黄白色球面。
 *
 * 实现:非实体、非方块,单次 draw call(覆盖全屏的 3 顶点 NDC 三角形)+ 自定义着色器
 * (隐式球面 ray-sphere 求交,片元从 NDC 逐像素重建视线方向),在
 * RenderLevelStageEvent.AFTER_LEVEL 阶段绘制,因此:
 * - 不受区块加载/视距影响:任何位置、任何视距下都能渲染(只要不被遮挡);
 * - 深度测试与方块/实体深度缓冲交互:被加载范围内方块遮挡的部分不显示,
 *   部分遮挡只显示未被遮挡的部分;
 * - 球体不写深度缓冲(depthMask=false),不遮挡任何方块与实体;
 * - 无实体、无 TE、无网格数据,每帧仅 3 顶点,开销可忽略;
 * - 视线方向逐像素从 NDC 重建,无顶点插值,转动视角不会扭曲变形。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SpaceSimulation.MOD_ID)
public class WorldSphereRenderer {

    /** 太阳半径范围(格),随世界种子在 [MIN, MAX] 间确定性变化。 */
    public static final double MIN_RADIUS = 200000.0;
    public static final double MAX_RADIUS = 500000.0;

    private static double currentRadius = MIN_RADIUS;
    private static long cachedSeed = Long.MIN_VALUE;

    /** 当前世界的太阳半径(格)。 */
    public static double getCurrentRadius() {
        return currentRadius;
    }

    /** 半径是否已按世界种子初始化(渲染器已运行)。 */
    public static boolean isRadiusInitialized() {
        return cachedSeed != Long.MIN_VALUE;
    }

    private static ShaderInstance sphereShader;
    private static boolean shaderReady = false;
    private static long startNs = 0;

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), "space_simulation:sphere", DefaultVertexFormat.POSITION),
                    shader -> {
                        sphereShader = shader;
                        shaderReady = true;
                        SpaceSimulation.LOGGER.info("[WorldSphere] sphere shader loaded");
                    });
        } catch (IOException e) {
            SpaceSimulation.LOGGER.error("[WorldSphere] failed to load sphere shader", e);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !shaderReady) {
            return;
        }

        try {
            RenderSystem.setShader(() -> sphereShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            // 相机 view 矩阵:AFTER_LEVEL 时 RenderSystem 栈与事件 poseStack 均已被重置为
            // identity,必须用 Camera 自行构造标准 view = (camera.rotation()^-1) * T(-camPos)
            net.minecraft.client.Camera cam = event.getCamera();
            net.minecraft.world.phys.Vec3 cp = cam.getPosition();
            org.joml.Matrix4f view = new org.joml.Matrix4f()
                    .rotation(cam.rotation().conjugate())
                    .translate((float) -cp.x, (float) -cp.y, (float) -cp.z);
            org.joml.Matrix4f invView = new org.joml.Matrix4f(view).invert();

            // 球心(世界原点)在 view 空间的坐标,由 CPU 按相机矩阵计算后传入片元着色器
            org.joml.Vector3f centerView = new org.joml.Vector3f(0.0F, 0.0F, 0.0F).mulPosition(view);
            com.mojang.blaze3d.shaders.Uniform uCenter = sphereShader.getUniform("SphereCenterView");
            if (uCenter != null) {
                uCenter.set(centerView.x, centerView.y, centerView.z);
            }
            com.mojang.blaze3d.shaders.Uniform uInvView = sphereShader.getUniform("InvView");
            if (uInvView != null) {
                uInvView.set(invView);
            }
            // 太阳半径:由世界种子确定性生成(200000 ~ 500000 格)
            if (mc.level != null) {
                long seed = 0L;
                net.minecraft.server.MinecraftServer srv = mc.getSingleplayerServer();
                if (srv != null) {
                    seed = srv.overworld().getSeed();
                }
                if (seed != cachedSeed) {
                    cachedSeed = seed;
                    java.util.Random rnd = new java.util.Random(seed);
                    currentRadius = MIN_RADIUS + rnd.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
                    SpaceSimulation.LOGGER.info("[WorldSphere] seed {} -> sun radius {}", seed, currentRadius);
                }
            }
            com.mojang.blaze3d.shaders.Uniform uRadius = sphereShader.getUniform("SphereRadius");
            if (uRadius != null) {
                uRadius.set((float) currentRadius);
            }
            // 屏幕尺寸(主渲染目标):片元着色器据此从 NDC 逐像素重建视线方向
            com.mojang.blaze3d.shaders.Uniform uSize = sphereShader.getUniform("ScreenSize");
            if (uSize != null) {
                uSize.set((float) mc.getMainRenderTarget().width, (float) mc.getMainRenderTarget().height);
            }
            // 时间(秒):驱动太阳自转/沸腾/脉动动画
            if (startNs == 0L) {
                startNs = System.nanoTime();
            }
            com.mojang.blaze3d.shaders.Uniform uTime = sphereShader.getUniform("Time");
            if (uTime != null) {
                uTime.set((System.nanoTime() - startNs) / 1.0e9F);
            }

            // 深度测试 LEQUAL(日面深度>远平面时钳到1.0 也能显示),只读不写深度,
            // 关闭背面剔除(全屏三角形正反都覆盖);加法混合(ONE,ONE)产生发光感
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);

            // 覆盖全屏的 NDC 大三角形:顶点只在远平面产生片元,方向由片元着色器重建,
            // 转动视角不会因面边界插值而扭曲
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            builder.addVertex(-1.0F, -1.0F, 0.0F);
            builder.addVertex(3.0F, -1.0F, 0.0F);
            builder.addVertex(-1.0F, 3.0F, 0.0F);
            BufferUploader.drawWithShader(builder.build());

            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        } catch (Exception e) {
            SpaceSimulation.LOGGER.error("[WorldSphere] render error", e);
        }

        // 测试截图钩子:球体已画入主帧缓冲,同帧读取截图
        SphereVisualTest.onAfterLevelRender(mc);
    }

    /** 读取主帧缓冲并保存 PNG(渲染线程调用,TestFlow 截图用)。 */
    public static boolean captureScreenshot(Minecraft mc) {
        try {
            RenderTarget target = mc.getMainRenderTarget();
            target.bindRead();
            int w = target.width;
            int h = target.height;
            ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            NativeImage img = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = ((h - 1 - y) * w + x) * 4; // OpenGL 原点在左下,翻转 Y
                    int r = buf.get(i) & 0xFF;
                    int g = buf.get(i + 1) & 0xFF;
                    int b = buf.get(i + 2) & 0xFF;
                    int a = buf.get(i + 3) & 0xFF;
                    img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            Path dir = mc.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(dir);
            Path file = dir.resolve("sphere_check_" + System.currentTimeMillis() + ".png");
            img.writeToFile(file);
            img.close();
            SpaceSimulation.LOGGER.info("[WorldSphere] screenshot saved: {}", file.toAbsolutePath());
            return true;
        } catch (Exception e) {
            SpaceSimulation.LOGGER.error("[WorldSphere] screenshot failed", e);
            return false;
        }
    }
}
