package org.xyccwa.space_simulation.client;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

/**
 * 「世界中心的太阳」的径向方向来源。
 *
 * <p>本模组把太阳放在世界原点 {@code (0,0,0)}，光是<b>向外辐射</b>的，因此场景里任何一点
 * 的受光方向都等于该点相对原点的方向——方向本身随位置连续变化，而不是一个固定的平行光。
 *
 * <p>但渲染层面不能直接用「点光源」：玩家常在离原点数十万到数百万格处，而阴影/光照的
 * 有效作用范围只有几十到几百格。在这个局部范围内光线方向的变化量
 * （{@code shadowDistance / distanceToSun}，约 {@code 1e-4} 弧度量级）远小于一个像素，
 * 因此<b>局部光线可以精确地当作平行光</b>，方向取 {@code normalize(cameraPos)}
 * （从太阳指向相机，即光的传播方向）。这是本实现绕开立方体阴影贴图的关键前提。
 *
 * <p>精度：相机绝对坐标在 1e6 处 f32 的 ULP 约 0.06 格，归一化后对方向的影响约 1e-7，
 * 完全可忽略；因此这里只需要方向，从不把绝对坐标传进着色器。
 *
 * <p>仅在<b>本模组内建的光影包</b>真正启用时接管（避免干扰用户启用其它光影包时的行为）。
 */
public final class RadialSunDirection {

    /** 内建光影包的目录/包名（{@code shaderpacks/<此名>}）。 */
    public static final String SHADER_PACK_NAME = "SpaceSimulation";

    private RadialSunDirection() {
    }

    /** 内建光影包是否正由 Iris 使用。 */
    public static boolean enabled() {
        try {
            String name = Iris.getCurrentPackName();
            return name != null && name.equals(SHADER_PACK_NAME);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 相机世界坐标；不可用时返回 {@code null}。
     */
    public static Vec3 cameraPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.level == null) {
            return null;
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam == null) {
            return null;
        }
        Vec3 p = cam.getPosition();
        return p == null ? null : p;
    }

    /**
     * <b>指向太阳</b>的世界空间方向，单位为 {@code 100}（Iris {@code sunPosition} 的标准语义：
     * 归一化方向 × 100）。Iris 在用它构造阴影剔除视锥前会再次归一化，故长度不承载信息。
     *
     * @return 方向向量；相机不可用或恰在世界原点时返回 {@code null}（调用方回退原版行为）
     */
    public static Vector4f worldToSun() {
        Vec3 p = cameraPosition();
        if (p == null) {
            return null;
        }
        double len = p.length();
        if (len < 1.0E-3) {
            // 相机恰在世界原点（太阳内部）：方向无定义，回退原版
            return null;
        }
        float s = (float) (-100.0 / len);
        return new Vector4f((float) (p.x * s), (float) (p.y * s), (float) (p.z * s), 0.0F);
    }

    /**
     * 相机到世界中心（太阳）的距离，单位格。
     *
     * <p>这个值由 mixin 通过 {@code UniformHolder.uniform1f} 注册成专用 uniform
     * {@code spaceSunDistance} 直接传给光影包，而不是去复用 Iris 的 {@code sunPosition}
     * 长度或 {@code cameraPosition}：
     * <ul>
     *   <li>{@code cameraPosition} 会以 30000 格为周期折回，无法用于求距离；</li>
     *   <li>把距离塞进 {@code sunPosition} 的长度会让该 uniform 的语义偏离 Iris 约定，
     *       实测也不可靠 —— 独立 uniform 是唯一没有歧义的做法。</li>
     * </ul>
     *
     * @return 距离（格）；相机不可用时返回 {@code 0}（调用方应视为「无衰减」）
     */
    public static double sunDistance() {
        Vec3 p = cameraPosition();
        return p == null ? 0.0 : p.length();
    }
}
