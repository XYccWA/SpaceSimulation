package org.xyccwa.space_simulation.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 径向光的阴影视图矩阵。
 *
 * <p>Iris 的阴影视图矩阵由 {@code ShadowMatrices.createBaselineModelViewMatrix} 构造，其参数化
 * 是固定的欧拉链 {@code Rx(90) · Rz(-(shadowAngle + 0.25) · 360) · Rx(sunPathRotation)}，
 * 其中 {@code shadowAngle} 来自世界时间 {@code ClientLevel.getTimeOfDay}。这条链只能表达
 * 「绕固定大圆运动的原版太阳」，而世界中心太阳的径向方向 {@code normalize(cameraPos)}
 * 会随玩家在三维空间漫游覆盖整个球面，<b>无法</b>用该参数化表达——所以必须在这里接管整个
 * 旋转，而不是只替换一个角度。
 *
 * <p>与 Iris 的兼容点（勿改）：
 * <ul>
 *   <li>阴影视图矩阵作用在 <b>player space</b>（相机相对世界坐标）上，Iris 官方
 *       shadow 采样写法即为 {@code shadowModelView * vec4(feetPlayerPos, 1.0)}。因此这里
 *       <b>仍然调用</b> {@link ShadowMatrices#snapModelViewToGrid}，用完全相同的网格对齐平移，
 *       保证坐标空间约定与 Iris 渲染 shadow pass 时一致；</li>
 *   <li>只替换旋转部分：让光传播方向映射到光空间的 {@code -Z}（与
 *       {@code createBaselineModelViewMatrix} 中太阳位于正上方时的结果一致），
 *       正交阴影投影 {@code shadowProjection} 的 near/far 就仍然沿 -Z 有效。</li>
 * </ul>
 */
public final class RadialShadowMatrices {

    private RadialShadowMatrices() {
    }

    /**
     * 构造沿「世界中心 → 相机」方向的阴影视图矩阵。
     *
     * @param intervalSize Iris 的 {@code shadowIntervalSize}，用于网格对齐（防阴影随相机爬行）
     * @return 阴影视图 PoseStack；不可用时返回 {@code null}，调用方回退 Iris 原版矩阵
     */
    public static PoseStack create(float intervalSize) {
        if (!RadialSunDirection.enabled()) {
            return null;
        }
        Vec3 p = RadialSunDirection.cameraPosition();
        if (p == null) {
            return null;
        }
        double len = p.length();
        if (len < 1.0E-3) {
            // 相机恰在世界原点：方向无定义
            return null;
        }

        // 光传播方向 f = 从太阳(原点)射向相机；lookAt 会把 f 映射到光空间的 -Z，
        // 即与 Iris 原版「太阳在正上方时」的约定完全一致。
        float fx = (float) (p.x / len);
        float fy = (float) (p.y / len);
        float fz = (float) (p.z / len);

        // 参考上方向：避免与 f 平行（极点附近退化为世界 Z）
        float ux = 0.0F;
        float uy = 1.0F;
        float uz = 0.0F;
        if (Math.abs(fy) > 0.99F) {
            ux = 0.0F;
            uy = 0.0F;
            uz = 1.0F;
        }

        Matrix4f rotation = new Matrix4f().lookAt(0.0F, 0.0F, 0.0F, fx, fy, fz, ux, uy, uz);

        PoseStack pose = new PoseStack();
        pose.last().pose().set(rotation);
        pose.last().normal().set(new Matrix3f(rotation));

        // 网格对齐：把阴影视图的平移量化到 intervalSize 网格（作用在 player space 上），
        // 量化后光空间位置只依赖 floor(camPos / intervalSize)，使阴影图纹素在世界中保持稳定。
        //
        // 这里自己用 double 取模，而不是调用 ShadowMatrices.snapModelViewToGrid ——
        // 后者内部是 `((float) x % intervalSize)`，而相机坐标在百万格处 f32 的 ULP 约 0.06 格，
        // 与一个纹素（shadowIntervalSize = 0.125 格）同量级，会让量化在边界处错格、阴影抖动。
        if (intervalSize > 0.0F) {
            double half = intervalSize / 2.0;
            pose.last().pose().translate(
                    (float) (p.x % intervalSize - half),
                    (float) (p.y % intervalSize - half),
                    (float) (p.z % intervalSize - half));
        }
        return pose;
    }
}
