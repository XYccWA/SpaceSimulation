package org.xyccwa.space_simulation.asteroid.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小行星持久化台账 —— 记录"哪些小行星被玩家改动过"以及"它们的方块副本存在 sable 存储的哪儿"。
 *
 * <h2>为什么需要这份台账</h2>
 * sable 自己会把子层级落盘到 {@code <world>/sublevels/}（见 {@code SubLevelHoldingChunkMap#saveAll}），
 * 但它<b>不记录"这颗是谁"</b>：唯一的线索是我们在实体化时设的 {@code display_name = asteroid_<id>}。
 * 于是：
 * <ul>
 *   <li><b>改动标记</b>：只有被玩家动过方块的小行星才值得长期占用存档空间，未改动的一律在卸载时丢弃
 *       （内容与结构模板重建等价）。标记由 {@link AsteroidPersistence#markChanged} 写入，调用方是
 *       方块编辑检测（见 {@code AsteroidEditTracker}）。</li>
 *   <li><b>副本指针</b>：卸载时记录 sable 落盘后的指针（chunk + 索引），重新加载时用它
 *       {@code SubLevelHoldingChunkMap#snatchAndLoad} 直接取回，无需扫描整个存储目录。
 *       指针失效时还有一层按名字扫描的兜底（见 {@code AsteroidEntityifier#findStoredByName}）。</li>
 * </ul>
 *
 * <p>本类<b>不引用任何 sable 类型</b>：sable 是可选依赖，缺少它时本类仍必须能加载。
 * 指针因此拆成原始字段存储。
 */
public final class AsteroidPersistence extends SavedData {

    public static final String FILE_ID = "space_simulation_asteroids";

    /** 已被玩家改动过、需要跨会话保留的小行星 id。 */
    private final Set<Long> changed = new HashSet<>();

    /** 已落盘副本的位置：id → 指针。指针失效（chunk 迁移等）时回退扫描。 */
    private final Map<Long, Stored> stored = new HashMap<>();

    public AsteroidPersistence() {}

    /** 一份落盘副本的定位信息（对应 sable 的 GlobalSavedSubLevelPointer，但不引用其类型）。 */
    public record Stored(UUID uuid, int chunkX, int chunkZ, short storageIndex, short subLevelIndex) {}

    public static AsteroidPersistence getOrLoad(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AsteroidPersistence::new,
                        (tag, provider) -> load(tag),
                        DataFixTypes.LEVEL),
                FILE_ID);
    }

    private static AsteroidPersistence load(CompoundTag tag) {
        AsteroidPersistence data = new AsteroidPersistence();
        ListTag list = tag.getList("asteroids", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long id = entry.getLong("id");
            data.changed.add(id);
            if (entry.hasUUID("uuid")) {
                data.stored.put(id, new Stored(
                        entry.getUUID("uuid"),
                        entry.getInt("chunk_x"),
                        entry.getInt("chunk_z"),
                        entry.getShort("storage_index"),
                        entry.getShort("sub_level_index")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (long id : this.changed) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", id);
            Stored s = this.stored.get(id);
            if (s != null) {
                entry.putUUID("uuid", s.uuid());
                entry.putInt("chunk_x", s.chunkX());
                entry.putInt("chunk_z", s.chunkZ());
                entry.putShort("storage_index", s.storageIndex());
                entry.putShort("sub_level_index", s.subLevelIndex());
            }
            list.add(entry);
        }
        tag.put("asteroids", list);
        return tag;
    }

    // ---------- 改动标记 ----------

    /** 这颗小行星是否被玩家改动过（决定卸载时保留还是丢弃）。 */
    public boolean isChanged(long id) {
        return this.changed.contains(id);
    }

    /** 标记为"已改动"：此后它的方块状态必须跨会话保留。 */
    public void markChanged(long id) {
        if (this.changed.add(id)) {
            this.setDirty();
        }
    }

    /** 该颗的落盘副本指针（可能为 null：改动过但尚未落盘，或指针已失效）。 */
    @Nullable
    public Stored stored(long id) {
        return this.stored.get(id);
    }

    /** 记录落盘副本指针（卸载链路在 sable 写完盘后调用）。 */
    public void rememberStored(long id, UUID uuid, int chunkX, int chunkZ, short storageIndex, short subLevelIndex) {
        Stored old = this.stored.get(id);
        Stored now = new Stored(uuid, chunkX, chunkZ, storageIndex, subLevelIndex);
        if (!now.equals(old)) {
            this.stored.put(id, now);
            this.setDirty();
        }
    }

    /** 副本已不存在（被删/损坏）：清掉指针，避免每次恢复都白跑一趟。 */
    public void forgetStored(long id) {
        if (this.stored.remove(id) != null) {
            this.setDirty();
        }
    }

    /** 不再需要持久化的 id（副本已丢弃）。 */
    public void forget(long id) {
        boolean dirty = this.changed.remove(id);
        dirty |= this.stored.remove(id) != null;
        if (dirty) {
            this.setDirty();
        }
    }

    /** 全部改动过的 id（只读视图，供启动对账与命令回显）。 */
    public Set<Long> changedIds() {
        return Collections.unmodifiableSet(this.changed);
    }

    /** 已记下副本指针的颗数（应该 ≤ {@link #changedIds()} 的大小，差值 = 改动后还没落盘的）。 */
    public int storedCount() {
        return this.stored.size();
    }
}
