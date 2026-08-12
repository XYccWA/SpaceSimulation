package org.xyccwa.space_simulation.hitbox;

import cn.anecansaitin.hitboxapi.api.common.attachment.IEntityColliderHolder;
import cn.anecansaitin.hitboxapi.api.common.collider.battle.IHurtCollider;
import cn.anecansaitin.hitboxapi.common.HitboxDataAttachments;
import cn.anecansaitin.hitboxapi.common.attachment.EntityColliderHolder;
import cn.anecansaitin.hitboxapi.common.collider.battle.hurt.HurtLocalOBB;
import cn.anecansaitin.hitboxapi.common.collider.local.EntityCoordinateConverter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.xyccwa.space_simulation.api.EntityRotation;

import java.util.Map;

/**
 * 玩家旋转碰撞箱的静态辅助（服务端与客户端共用）。
 *
 * 身体盒 = 0.6×1.8×0.6（底心在 position），绕 position 旋转——与模型渲染的 pivot
 * （LivingEntityRendererMixin 的 mulPose 绕实体 position）完全一致，q=identity 时恰好
 * 等于原版盒，站立/正常飞行行为不变。
 *
 * 两套盒同源：
 * - {@link #rotatedBoundingBox}：身体盒旋转后的世界对齐 AABB，供 makeBoundingBox 注入，
 *   驱动原版所有碰撞系统（移动、实体、战斗射线、剔除、F3+B 白盒）。
 * - {@link #update}：每 tick 驱动 Hitbox API 的 HurtLocalOBB（F3+B 真旋转绿盒 + 服务端战斗判定）。
 */
public final class PlayerHitbox {

    /** 玩家身体 OBB 在 holder 中的 key。 */
    public static final String KEY = "space_sim_body";

    private PlayerHitbox() {
    }

    /**
     * 每 tick 驱动玩家的真旋转 OBB（服务端+客户端都跑，挂在 EntityMixin.spaceSim$onTick）。
     *
     * 必须每 tick 从本地四元数驱动：Hitbox API 的 EntityCoordinateConverter 只跟随位置
     * （rotation 恒为 identity），且库的增量同步（changelog）不记录 setLocalRotation/setLocalCenter，
     * 因此旋转只能由本地每 tick 重算，不能靠库的 NBT 同步。
     */
    public static void update(Entity entity) {
        if (!(entity instanceof EntityRotation rot) || !rot.hasOrientation()) {
            return;
        }
        Quaternionf q = rot.getOrientation();
        // 默认 supplier 返回 null，getData 不会自动建 holder，必须显式创建 + setData
        IEntityColliderHolder holder = entity.getExistingData(HitboxDataAttachments.COLLISION).orElse(null);
        if (holder == null) {
            holder = new EntityColliderHolder(entity);
            entity.setData(HitboxDataAttachments.COLLISION, holder);
        }
        EntityCoordinateConverter conv = holder.getCoordinateConverter();
        conv.update(); // 刷新 position（库的 LevelTick / renderer 也这么做）

        float hw = entity.getBbWidth() * 0.5F;
        float hh = entity.getBbHeight() * 0.5F;
        Map<String, IHurtCollider> hurt = holder.getHurtBox();
        HurtLocalOBB obb = (HurtLocalOBB) hurt.get(KEY);
        if (obb == null) {
            // 关键：converter 的 rotation 恒为 identity，localCenter 必须预旋转成世界偏移，
            // 否则 OBB 中心恒在"直立中心"（position+(0,hh,0)），横飞时与旋转盒错位。
            obb = new HurtLocalOBB(1.0F, new Vector3f(hw, hh, hw),
                    new Vector3f(0.0F, hh, 0.0F).rotate(q), q, conv);
            holder.addHurtBox(KEY, obb);
        }
        obb.setLocalRotation(q);
        obb.setLocalCenter(new Vector3f(0.0F, hh, 0.0F).rotate(q));
        obb.setHalfExtents(new Vector3f(hw, hh, hw));
    }

    /**
     * 身体盒（底心在 position，尺寸 getBbWidth×getBbHeight×getBbWidth）绕 position 旋转 q
     * 后 8 个角点的世界对齐 AABB。q=identity 时等于原版盒 [x±w/2, y, y+h, z±w/2]。
     */
    public static AABB rotatedBoundingBox(Entity entity, Quaternionf q) {
        float hw = entity.getBbWidth() * 0.5F;
        float h = entity.getBbHeight();
        double px = entity.getX(), py = entity.getY(), pz = entity.getZ();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) { // 角点 (±hw, {0,h}, ±hw)
            float cx = (i & 1) == 0 ? -hw : hw;
            float cy = (i & 2) == 0 ? 0.0F : h;
            float cz = (i & 4) == 0 ? -hw : hw;
            corner.set(cx, cy, cz).rotate(q);
            double wx = px + corner.x, wy = py + corner.y, wz = pz + corner.z;
            minX = Math.min(minX, wx);
            maxX = Math.max(maxX, wx);
            minY = Math.min(minY, wy);
            maxY = Math.max(maxY, wy);
            minZ = Math.min(minZ, wz);
            maxZ = Math.max(maxZ, wz);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
