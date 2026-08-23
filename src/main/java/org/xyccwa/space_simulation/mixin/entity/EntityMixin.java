package org.xyccwa.space_simulation.mixin.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.api.EntityRotation;
import org.xyccwa.space_simulation.hitbox.PlayerHitbox;

/**
 * 为所有实体注入四元数朝向存储。
 *
 * - turn()：鼠标横移=偏航、纵移=俯仰，全部改为绕机体轴的四元数旋转（右乘 = 机体坐标系），
 *   彻底绕开欧拉角的 ±90° 俯仰限制与万向节死锁。
 * - getViewVector()/getLookAngle()：返回四元数朝向对应的前向方向，使准星/射线/瞄准始终与视角一致。
 * - tick()：保持原有 noGravity 行为（太空无重力）。
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements EntityRotation {

    @Unique
    private final Quaternionf spaceSim$orientation = new Quaternionf();
    @Unique
    private final Quaternionf spaceSim$prevOrientation = new Quaternionf();
    @Unique
    private final Quaternionf spaceSim$displayOrientation = new Quaternionf();
    @Unique
    private boolean spaceSim$displayInitialized = false;
    @Unique
    private int spaceSim$moveMask = 0;

    @Override
    public boolean hasOrientation() {
        return (Object) this instanceof Player;
    }

    @Override
    public Quaternionf getOrientation() {
        return new Quaternionf(this.spaceSim$orientation);
    }

    @Override
    public Quaternionf getPrevOrientation() {
        return new Quaternionf(this.spaceSim$prevOrientation);
    }

    @Override
    public void setOrientation(Quaternionf orientation) {
        // prevOrientation 不在每次更新时跟随——它只在每个 tick 开始被快照一次（见 spaceSim$onTick），
        // 这样渲染时 slerp(prev, current, partialTick) 的窗口恰好是"上一个 tick 至今"，才是正确的插值。
        this.spaceSim$orientation.set(orientation).normalize();
        this.syncEulerAngles();
        // 原地旋转（转向/滚转、服务器控制包、远程朝向包）后立即重建碰撞盒，
        // 否则 bb 要等到下一次 move()/setPos 才更新，贴墙旋转时碰撞与 F3+B 白盒滞后一截。
        this.spaceSim$refreshBoundingBox();
    }

    @Override
    public Quaternionf getInterpolatedOrientation(float partialTick) {
        return this.spaceSim$prevOrientation.slerp(this.spaceSim$orientation, partialTick, new Quaternionf());
    }

    @Override
    public Quaternionf getSmoothedOrientation() {
        if (!this.spaceSim$displayInitialized) {
            this.spaceSim$displayOrientation.set(this.spaceSim$orientation);
            this.spaceSim$displayInitialized = true;
        }
        return new Quaternionf(this.spaceSim$displayOrientation);
    }

    @Override
    public void smoothOrientation(float alpha) {
        if (!this.spaceSim$displayInitialized) {
            this.spaceSim$displayOrientation.set(this.spaceSim$orientation);
            this.spaceSim$displayInitialized = true;
        }
        // 显示朝向指数缓动追向实际朝向。slerp(a, b, t) 在 a 处原地朝 b 插值 t。
        // 帧率无关：alpha 已由外部按帧时间换算，低帧率也能把大突变铺平成连续滑动。
        this.spaceSim$displayOrientation.slerp(this.spaceSim$orientation, Mth.clamp(alpha, 0.0F, 1.0F)).normalize();
    }

    @Override
    public int getMoveMask() {
        return this.spaceSim$moveMask;
    }

    @Override
    public void setMoveMask(int moveMask) {
        this.spaceSim$moveMask = moveMask;
    }

    @Override
    public void syncEulerAngles() {
        Entity self = (Entity) (Object) this;
        Vector3f forward = new Vector3f(0.0F, 0.0F, 1.0F).rotate(this.spaceSim$orientation);
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float pitch = (float) Math.toDegrees(Math.asin(-forward.y));
        // yRotO/xRotO 取上一个 tick 快照的欧拉角，保证原版 yRotO/yRot 插值语义正确（prev 恒为 1 tick 前）
        Vector3f prevForward = new Vector3f(0.0F, 0.0F, 1.0F).rotate(this.spaceSim$prevOrientation);
        float prevYaw = (float) Math.toDegrees(Math.atan2(-prevForward.x, prevForward.z));
        float prevPitch = (float) Math.toDegrees(Math.asin(-prevForward.y));
        self.setYRot(yaw);
        self.setXRot(pitch);
        self.yRotO = prevYaw;
        self.xRotO = prevPitch;
        if (self instanceof LivingEntity living) {
            living.setYHeadRot(yaw);
            living.setYBodyRot(yaw);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void spaceSim$onTick(CallbackInfo ci) {
        // 每个 tick 开始时把当前朝向快照为上一朝向，供渲染期 slerp 插值使用
        this.spaceSim$prevOrientation.set(this.spaceSim$orientation);
        ((Entity) (Object) this).setNoGravity(true);
        // 每 tick 兜底重建碰撞盒（原地旋转等不经过 setPos 的场景），并驱动 Hitbox API 的旋转 OBB
        this.spaceSim$refreshBoundingBox();
        PlayerHitbox.update((Entity) (Object) this);
    }

    /**
     * 让碰撞盒跟随 6DOF 旋转：makeBoundingBox 是所有 setPos/move 重建 AABB 的唯一工厂，
     * 在此把身体盒绕 position 旋转后的世界对齐 AABB 返回，则移动碰撞、实体碰撞、战斗射线、
     * 剔除与 F3+B 白盒全部自动跟随。q=identity 时等于原版盒，站立/正常飞行行为不变。
     */
    @Inject(method = "makeBoundingBox", at = @At("HEAD"), cancellable = true)
    private void spaceSim$onMakeBoundingBox(CallbackInfoReturnable<AABB> cir) {
        if (!this.hasOrientation() || this.spaceSim$orientation == null) {
            return; // 非玩家 / 构造期 mixin 字段未初始化：走原版
        }
        Entity self = (Entity) (Object) this;
        // 睡觉姿态走原版小盒（渲染侧 LivingEntityRendererMixin 对 SLEEPING 同样跳过朝向）
        if (self.hasPose(Pose.SLEEPING)) {
            return;
        }
        // 构造期 Entity 构造器末尾的 setPos(0,0,0) 会调到这里，此时 dimensions 已赋值但
        // 仍防御性跳过（尺寸为 0 的原版盒兜底）
        if (self.getBbWidth() <= 0.0F || self.getBbHeight() <= 0.0F) {
            return;
        }
        cir.setReturnValue(PlayerHitbox.rotatedBoundingBox(self, this.spaceSim$orientation));
    }

    /** makeBoundingBox 是 protected，mixin 类不继承 Entity，须 @Shadow 声明才能调用。 */
    @Shadow
    protected abstract AABB makeBoundingBox();

    /** 用当前朝向立即重建碰撞盒（setOrientation 与每 tick 兜底调用）。 */
    @Unique
    private void spaceSim$refreshBoundingBox() {
        if (this.hasOrientation()) {
            // makeBoundingBox() 是被注入后的方法：非玩家/边界情况自动落回原版盒
            ((Entity) (Object) this).setBoundingBox(this.makeBoundingBox());
        }
    }

    /**
     * 禁用原版窒息伤害：baseTick 的 suffocation 检查经 isInWall()（仅服务端）。
     * 旋转碰撞盒（makeBoundingBox 世界对齐包围盒）贴近/穿过小行星时盒缘会碰到方块，
     * 触发持续掉血；太空飞行场景禁用后便于测试碰撞行为（贴墙/钻洞不掉血）。
     */
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void spaceSim$noSuffocation(CallbackInfoReturnable<Boolean> cir) {
        if (this.hasOrientation()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 替换原版 turn()：yawDelta 绕机体 up 轴、pitchDelta 绕机体 right 轴做四元数旋转。
     * 右乘 rotationY(-yawDelta*0.15) / rotationX(pitchDelta*0.15) 即绕机体自身轴旋转，
     * 与鼠标右移->视角右转、鼠标上移->视角上抬的原有手感一致。
     */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void spaceSim$onTurn(double yawDelta, double pitchDelta, CallbackInfo ci) {
        if (!this.hasOrientation()) {
            return;
        }
        ci.cancel();
        Quaternionf q = this.getOrientation();
        // 关键：原版 turn() 里 0.15 作用在"度"上（加进 getYRot() 的单位是度），而 rotationY/rotationX
        // 接收弧度。若直接把 0.15 当弧度用，灵敏度会是原版的 180/π≈57.3 倍——鼠标每 1 像素 = 8.6°
        // （默认灵敏度），慢移鼠标时输入被量化成粗颗粒步进，就是"小角度瞬移"。必须转成弧度。
        float yawRad = (float) Math.toRadians(-yawDelta * 0.15);
        float pitchRad = (float) Math.toRadians(pitchDelta * 0.15);
        q.mul(new Quaternionf().rotationY(yawRad));
        q.mul(new Quaternionf().rotationX(pitchRad));
        q.normalize();
        this.setOrientation(q);
        Entity self = (Entity) (Object) this;
        if (self.getVehicle() != null) {
            self.getVehicle().onPassengerTurned(self);
        }
    }

    /**
     * 拾取/瞄准射线起点严格跟随身体朝向：原版 getEyePosition 返回 (x, y+eh, z)
     * （沿世界 Y 偏移眼高），旋转/滚转/倒飞时"直立眼位"与相机位置分离，射线从错误
     * 起点打出，准星所指与命中对象错位。
     *
     * 偏移方向必须用 getOrientation()（当前实际朝向，零滞后）——不能用
     * getSmoothedOrientation()/getInterpolatedOrientation()：平滑缓动朝向在滚转/倒飞时
     * 严格落后实际姿态（tau=0.04s，快速滚转残差可达数十度），眼位会沿"半转半未转"的
     * up 轴偏移，起点仍与身体真实眼位分离，实测滚转/倒飞下准星与实际命中不一致。
     * 原版 getViewVector/getEyePosition 本就只插值位置、不插值朝向（眼睛始终严格贴在
     * 当前姿态上），这里保持一致：位置 lerp、朝向 current。
     */
    // 注意：Entity 有 getEyePosition()（无参）与 getEyePosition(float) 两个重载，
    // @Inject 的 method 必须写完整描述符精确绑定到 float 版，否则 Sponge 两重重载歧义
    // （handler 签名只匹配 float 版）会导致本注入被静默跳过——表现就是"起点仍原版
    // 世界 Y 眼位、俯仰/滚转时准星与实际命中错位、偏航天然正常"（实测）。
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void spaceSim$onGetEyePosition(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (!this.hasOrientation()) {
            return;
        }
        Entity self = (Entity) (Object) this;
        // 睡觉姿态走原版低眼位（与 makeBoundingBox / 渲染侧 SLEEPING 跳过一致）
        if (self.hasPose(Pose.SLEEPING)) {
            return;
        }
        float eh = self.getEyeHeight();
        if (eh <= 0.0F) {
            return; // 防御（正常不会）
        }
        // 位置插值语义与原版一致（xo→x 按 partialTick lerp）；朝向不插值，严格用当前身体姿态
        double ex = Mth.lerp((double) partialTick, self.xo, self.getX());
        double ey = Mth.lerp((double) partialTick, self.yo, self.getY());
        double ez = Mth.lerp((double) partialTick, self.zo, self.getZ());
        Quaternionf q = this.getOrientation();
        Vector3f bodyUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(q);
        cir.setReturnValue(new Vec3(
                ex + (double) bodyUp.x * eh,
                ey + (double) bodyUp.y * eh,
                ez + (double) bodyUp.z * eh));
    }

    /** 让所有读取视角方向的逻辑跟随四元数朝向（含倒飞时俯仰超过 ±90° 的情况）。 */
    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void spaceSim$onGetViewVector(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (!this.hasOrientation()) {
            return;
        }
        // 用当前朝向（零滞后）：相机已零滞后跟随鼠标，准星/射线必须与相机同步，否则转向时准星会滞后一截。
        // 服务端 partialTick 无意义、当前即权威；客户端远程实体的 getViewVector 不用于渲染，直接用当前即可。
        Vector3f forward = new Vector3f(0.0F, 0.0F, 1.0F).rotate(this.getOrientation());
        cir.setReturnValue(new Vec3(forward.x, forward.y, forward.z));
    }

    @Inject(method = "getLookAngle", at = @At("HEAD"), cancellable = true)
    private void spaceSim$onGetLookAngle(CallbackInfoReturnable<Vec3> cir) {
        if (!this.hasOrientation()) {
            return;
        }
        Vector3f forward = new Vector3f(0.0F, 0.0F, 1.0F).rotate(this.spaceSim$orientation);
        cir.setReturnValue(new Vec3(forward.x, forward.y, forward.z));
    }
}
