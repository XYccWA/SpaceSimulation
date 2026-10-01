package org.xyccwa.space_simulation.mixin.iris;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * 内建光影包（Iris）mixin 的条件开关。
 *
 * <p>Iris 对本模组是<b>软依赖</b>：未安装 Iris 时，{@code space_simulation.iris.mixins.json}
 * 里的所有 mixin 目标类都不存在，直接应用会抛 {@code ClassNotFoundException} /
 * mixin target-not-found 而中断启动。这里在配置加载阶段用<b>资源探测</b>（不加载类，
 * 避免触发 Iris 的早期类初始化）判断 Iris 是否在类路径上，不在则整体跳过。
 *
 * <p>探测方式与 {@code SpaceSimulation#isRapierPresent()} 一致。
 */
public class IrisMixinPlugin implements IMixinConfigPlugin {

    /** Iris 主类在 jar 内的资源路径（用于存在性探测）。 */
    private static final String IRIS_MARKER = "net/irisshaders/iris/Iris.class";

    /** 本配置里的 mixin 是否应当应用（Iris 存在时才为 true）。 */
    private static boolean irisPresent;

    @Override
    public void onLoad(String mixinPackage) {
        irisPresent = detectIris();
    }

    private static boolean detectIris() {
        try {
            return IrisMixinPlugin.class.getClassLoader().getResource(IRIS_MARKER) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Iris 是否存在于运行时类路径。 */
    public static boolean isIrisPresent() {
        return irisPresent;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return irisPresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
