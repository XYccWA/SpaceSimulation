package org.xyccwa.space_simulation.asteroid.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.asteroid.AsteroidBeltDef;
import org.xyccwa.space_simulation.asteroid.AsteroidProximityService;
import org.xyccwa.space_simulation.asteroid.AsteroidTypeTable;
import org.xyccwa.space_simulation.asteroid.AsteroidUniverse;
import org.xyccwa.space_simulation.asteroid.AsteroidUniverseSource;
import org.xyccwa.space_simulation.asteroid.AsteroidVerticalLimit;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 小行星数据包加载器。
 *
 * 数据包布局：
 *   data/&lt;ns&gt;/asteroid_belt/&lt;id&gt;.json     环带定义（可多个，半径区间不得重叠）
 *   data/&lt;ns&gt;/asteroid_type/&lt;id&gt;.json     小行星类型定义（变体 + 权重）
 *   data/&lt;ns&gt;/structure/&lt;path&gt;.nbt        变体结构模板（原版结构模板路径）
 *
 * 环带 JSON：
 * <pre>
 * {
 *   "name": "内小行星带",
 *   "inner_radius": 1000000.0,
 *   "outer_radius": 1400000.0,
 *   "height": { "type": "inclination", "max_deg": 15.0 },
 *   "density": { "type": "mean_spacing", "blocks_per_asteroid": 1000000.0 },
 *   "max_eccentricity": 0.35,
 *   "inner_orbit_period_ticks": 62830000,
 *   "types": [ { "type": "space_simulation:stone", "weight": 6 } ]
 * }
 * </pre>
 * height 两种写法：{ "type": "inclination", "max_deg": 15 } 或 { "type": "altitude", "min": -2e4, "max": 2e4 }；
 * density 两种写法：{ "type": "total", "count": 12345678 } 或 { "type": "mean_spacing", "blocks_per_asteroid": 1e6 }。
 *
 * 类型 JSON：
 * <pre>
 * {
 *   "name": "石质小行星",
 *   "variants": [ { "name": "岩块", "structure": "space_simulation:asteroid/stone_a", "weight": 5 } ]
 * }
 * </pre>
 *
 * 三个入口共用 {@link #build(ResourceManager)} / {@link #loadNow(ResourceManager)}：
 *   1) 数据包重载（本类作为 reload listener，覆盖 /reload）；
 *   2) 服务器启动完成（SpaceSimulation 的 ServerStartedEvent，作为兜底）；
 *   3) 命令 /asteroid reload（手动即时重载 + 诊断输出）。
 *
 * 校验策略：单个文件出错只跳过该文件并记录；全部环带无效时回退到内置默认单带（系统始终可用）。
 */
public final class AsteroidDataLoader extends SimpleJsonResourceReloadListener {

    public static final String BELT_DIR = "asteroid_belt";
    public static final String TYPE_DIR = "asteroid_type";
    /** 变体引用的结构模板在数据包中的路径形态：structure/&lt;path&gt;.nbt。 */
    public static final String STRUCTURE_DIR = "structure";

    private static final Gson GSON = new Gson();

    public AsteroidDataLoader() {
        super(GSON, BELT_DIR);
    }

    /** 数据包重载入口（/reload）。 */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> beltJson,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        loadNow(resourceManager);
    }

    /** 解析并原子安装（服务器启动 / reload 监听 / 命令共用）。 */
    public static AsteroidCatalog loadNow(ResourceManager rm) {
        AsteroidCatalog catalog;
        try {
            catalog = build(rm);
        } catch (Throwable t) {
            SpaceSimulation.LOGGER.error("[小行星数据包] 解析异常，回退内置默认单带", t);
            List<String> errors = new ArrayList<>();
            errors.add("解析异常：" + t);
            catalog = new AsteroidCatalog(AsteroidUniverseSource.fallback(), errors, 0, 0, true);
        }
        AsteroidUniverse u = catalog.universe;
        for (String e : catalog.errors) {
            SpaceSimulation.LOGGER.warn("[小行星数据包] {}", e);
        }
        StringBuilder belts = new StringBuilder();
        for (AsteroidUniverse.Belt b : u.belts) {
            if (belts.length() > 0) belts.append(", ");
            belts.append(b.name).append('[')
                    .append(String.format("%.0f", b.innerRadius)).append('~')
                    .append(String.format("%.0f", b.outerRadius))
                    .append(" 块, ").append(String.format("%,d", b.totalCount)).append(" 颗]");
        }
        SpaceSimulation.LOGGER.info(
                "[小行星数据包] 加载完成：来源={} · 环带文件 {} · 类型文件 {} · 环带 {} 个 · 总数 {},{} · 警告 {} 条 · {}",
                catalog.fallback ? "内置回退" : "数据包",
                catalog.beltFileCount, catalog.typeFileCount, u.belts.length,
                u.totalCount / 1000L, u.totalCount % 1000L, catalog.errors.size(), belts);
        AsteroidProximityService.install(catalog);
        return catalog;
    }

    /** 纯解析（不安装）：供命令/调试观察结果。 */
    public static AsteroidCatalog build(ResourceManager rm) {
        List<String> errors = new ArrayList<>();
        try {
            TypesResult types = parseTypes(rm, errors);
            BeltsResult belts = parseBelts(rm, types, errors);
            if (belts.defs.isEmpty()) {
                errors.add("没有任何有效的环带定义（" + BELT_DIR + "，扫描到 " + belts.fileCount
                        + " 个文件），已回退到内置默认单带");
                return new AsteroidCatalog(AsteroidUniverseSource.fallback(), errors,
                        belts.fileCount, types.count, true);
            }
            try {
                AsteroidUniverse universe = AsteroidUniverseSource.build(types.table, belts.defs);
                return new AsteroidCatalog(universe, errors, belts.fileCount, types.count, false);
            } catch (RuntimeException ex) {
                errors.add("宇宙构造失败：" + ex + "；已回退到内置默认单带");
                return new AsteroidCatalog(AsteroidUniverseSource.fallback(), errors,
                        belts.fileCount, types.count, true);
            }
        } catch (Throwable t) {
            errors.add("数据包解析异常：" + t + "；已回退到内置默认单带");
            return new AsteroidCatalog(AsteroidUniverseSource.fallback(), errors, 0, 0, true);
        }
    }

    /**
     * 资源位置归一化为类型 id：去掉本目录前缀与 .json 后缀
     * （listResources 返回的 key 形如 {@code ns:asteroid_type/stone.json} → {@code ns:stone}）。
     */
    private static String normalizedId(ResourceLocation rl, String dir) {
        String path = rl.getPath();
        String prefix = dir + "/";
        if (path.startsWith(prefix)) path = path.substring(prefix.length());
        if (path.endsWith(".json")) path = path.substring(0, path.length() - ".json".length());
        return rl.getNamespace() + ":" + path;
    }

    /** belt 中引用的类型 id 归一化：同时接受 {@code ns:stone}、{@code ns:stone.json}、{@code ns:asteroid_type/stone.json}。 */
    private static String normalizeTypeRef(String ref) {
        String p = ref.trim();
        if (p.endsWith(".json")) p = p.substring(0, p.length() - ".json".length());
        int c = p.indexOf(':');
        String ns = c >= 0 ? p.substring(0, c) : "minecraft";
        String path = c >= 0 ? p.substring(c + 1) : p;
        String prefix = TYPE_DIR + "/";
        if (path.startsWith(prefix)) path = path.substring(prefix.length());
        return ns + ":" + path;
    }

    // ---------- 类型 ----------

    private static final class TypesResult {
        AsteroidTypeTable table = AsteroidTypeTable.EMPTY;
        int count;
    }

    private static TypesResult parseTypes(ResourceManager rm, List<String> errors) {
        TypesResult result = new TypesResult();
        Map<ResourceLocation, Resource> files =
                rm.listResources(TYPE_DIR, rl -> rl.getPath().endsWith(".json"));
        List<ResourceLocation> sorted = new ArrayList<>(files.keySet());
        Collections.sort(sorted);

        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String[]> vStructs = new ArrayList<>();
        List<String[]> vNames = new ArrayList<>();
        List<double[]> vCums = new ArrayList<>();

        for (ResourceLocation rl : sorted) {
            String id = normalizedId(rl, TYPE_DIR);
            try (Reader reader = files.get(rl).openAsReader()) {
                JsonObject o = GsonHelper.fromJson(GSON, reader, JsonObject.class);
                if (o == null) {
                    errors.add("类型文件为空：" + rl);
                    continue;
                }
                String name = GsonHelper.getAsString(o, "name", id);
                JsonArray variants = GsonHelper.getAsJsonArray(o, "variants");
                List<String> structs = new ArrayList<>();
                List<String> vnames = new ArrayList<>();
                List<Double> weights = new ArrayList<>();
                for (JsonElement ve : variants) {
                    if (!ve.isJsonObject()) continue;
                    JsonObject v = ve.getAsJsonObject();
                    double w = GsonHelper.getAsDouble(v, "weight", 1.0);
                    if (!(w > 0)) {
                        errors.add("类型 " + id + " 的变体权重必须 > 0，已忽略该变体");
                        continue;
                    }
                    String structure = GsonHelper.getAsString(v, "structure", "");
                    String vname = GsonHelper.getAsString(v, "name",
                            structure.isEmpty() ? ("variant_" + structs.size()) : structure);
                    if (!structure.isEmpty() && !structureExists(rm, structure)) {
                        errors.add("类型 " + id + " 引用的结构不存在：" + structure
                                + "（应为 data/<ns>/" + STRUCTURE_DIR + "/<path>.nbt）");
                    }
                    structs.add(structure);
                    vnames.add(vname);
                    weights.add(w);
                }
                if (weights.isEmpty()) {
                    errors.add("类型 " + id + " 没有任何有效变体，已跳过");
                    continue;
                }
                double total = 0;
                for (double w : weights) total += w;
                double[] cum = new double[weights.size()];
                double acc = 0;
                for (int i = 0; i < weights.size(); i++) {
                    acc += weights.get(i) / total;
                    cum[i] = i == weights.size() - 1 ? 1.0 : acc;
                }
                ids.add(id);
                names.add(name);
                vStructs.add(structs.toArray(new String[0]));
                vNames.add(vnames.toArray(new String[0]));
                vCums.add(cum);
            } catch (Exception ex) {
                errors.add("类型文件解析失败 " + rl + "：" + ex);
            }
        }

        result.count = ids.size();
        if (result.count > 0) {
            result.table = new AsteroidTypeTable(
                    ids.toArray(new String[0]),
                    names.toArray(new String[0]),
                    vStructs.toArray(new String[0][]),
                    vNames.toArray(new String[0][]),
                    vCums.toArray(new double[0][]));
        }
        return result;
    }

    /** 变体结构是否存在于数据包（原版结构模板 data/&lt;ns&gt;/structure/&lt;path&gt;.nbt）。 */
    private static boolean structureExists(ResourceManager rm, String structureId) {
        ResourceLocation rl = ResourceLocation.tryParse(structureId);
        if (rl == null) return false;
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                rl.getNamespace(), STRUCTURE_DIR + "/" + rl.getPath() + ".nbt");
        return rm.getResource(file).isPresent();
    }

    // ---------- 环带 ----------

    private static final class BeltsResult {
        final List<AsteroidBeltDef> defs = new ArrayList<>();
        int fileCount;
    }

    private static BeltsResult parseBelts(ResourceManager rm, TypesResult types,
                                          List<String> errors) {
        BeltsResult result = new BeltsResult();
        Map<ResourceLocation, Resource> files =
                rm.listResources(BELT_DIR, rl -> rl.getPath().endsWith(".json"));
        List<ResourceLocation> sorted = new ArrayList<>(files.keySet());
        Collections.sort(sorted);
        result.fileCount = sorted.size();
        List<AsteroidBeltDef> out = result.defs;

        for (ResourceLocation rl : sorted) {
            try (Reader reader = files.get(rl).openAsReader()) {
                JsonObject o = GsonHelper.fromJson(GSON, reader, JsonObject.class);
                if (o == null) {
                    errors.add("环带文件为空：" + rl);
                    continue;
                }
                String name = GsonHelper.getAsString(o, "name", rl.getPath());
                double inner = GsonHelper.getAsDouble(o, "inner_radius");
                double outer = GsonHelper.getAsDouble(o, "outer_radius");
                if (!(inner > 0) || !(outer > inner)) {
                    errors.add("环带 " + rl + " 半径非法（需 0 < inner_radius < outer_radius），已跳过");
                    continue;
                }
                double ecc = GsonHelper.getAsDouble(o, "max_eccentricity", 0.35);
                if (!(ecc >= 0) || ecc >= 1.0) {
                    errors.add("环带 " + rl + " 的 max_eccentricity 必须 ∈ [0,1)，已改用 0.35");
                    ecc = 0.35;
                }

                // 高度：两种写法
                JsonObject height = GsonHelper.getAsJsonObject(o, "height");
                String heightType = GsonHelper.getAsString(height, "type");
                double iMin, iMax;
                double yLo = Double.NaN, yHi = Double.NaN;
                if ("inclination".equals(heightType)) {
                    double maxDeg = GsonHelper.getAsDouble(height, "max_deg");
                    iMin = 0.0;
                    iMax = Math.toRadians(maxDeg);
                } else if ("altitude".equals(heightType)) {
                    yLo = GsonHelper.getAsDouble(height, "min", 0.0);
                    yHi = GsonHelper.getAsDouble(height, "max");
                    if (!(yHi > yLo)) {
                        errors.add("环带 " + rl + " 的 height.max 必须大于 min，已跳过");
                        continue;
                    }
                    // 语义：yHi/yLo 就是整带 |轨道 Y| 的上/下限（块）。
                    // y = r·sin i·sin u，整带最大 |y| = a_max(1+e_max)·sin i = outer(1+ecc)·sin i → 用 outer 反推。
                    double refA = outer * (1.0 + ecc);
                    iMax = Math.asin(clamp01(yHi / refA));
                    double lo = Math.asin(clamp01(yLo / refA));
                    iMin = Math.min(lo, iMax);
                } else {
                    errors.add("环带 " + rl + " 的 height.type 必须是 inclination 或 altitude，已跳过");
                    continue;
                }
                if (!(iMax > 0)) {
                    errors.add("环带 " + rl + " 换算出的倾角上限为 0，已跳过");
                    continue;
                }

                // 实体化硬约束：sable 子层级 Y 窗口（越界即被 sable 直接删除）。
                // 整带 |y| = outer(1+ecc)·sin i ≤ 上限，超出则压倾角并告警（不要把带做成生成不出来的）。
                double yLimit = AsteroidVerticalLimit.maxAbsY();
                double sinAllowed = yLimit / (outer * (1.0 + ecc));
                if (sinAllowed < Math.sin(iMax)) {
                    double requestedDeg = Math.toDegrees(iMax);
                    iMax = Math.asin(clamp01(sinAllowed));
                    if (iMin > iMax) {
                        iMin = iMax;
                    }
                    errors.add(String.format(Locale.ROOT,
                            "环带 %s 竖直范围超出实体化上限：请求倾角上限 %.4f° → 压到 %.4f°（|Y| ≤ %.0f 块）。%s",
                            rl, requestedDeg, Math.toDegrees(iMax), yLimit, AsteroidVerticalLimit.describe()));
                }

                // 密度：两种写法
                JsonObject density = GsonHelper.getAsJsonObject(o, "density");
                String densityType = GsonHelper.getAsString(density, "type");
                long count;
                if ("total".equals(densityType)) {
                    count = GsonHelper.getAsLong(density, "count");
                } else if ("mean_spacing".equals(densityType)) {
                    double spacing = GsonHelper.getAsDouble(density, "blocks_per_asteroid");
                    if (!(spacing > 0)) {
                        errors.add("环带 " + rl + " 的 blocks_per_asteroid 必须 > 0，已跳过");
                        continue;
                    }
                    double rMid = (inner + outer) * 0.5;
                    double f = Double.isNaN(yHi)
                            ? Math.max(0.0, Math.sin(iMax) - Math.sin(iMin))
                            : clamp01((yHi - yLo) / (2.0 * rMid));
                    double volume = (4.0 / 3.0) * Math.PI * (outer * outer * outer - inner * inner * inner) * f;
                    count = (long) Math.ceil(volume / (spacing * spacing * spacing));
                } else {
                    errors.add("环带 " + rl + " 的 density.type 必须是 total 或 mean_spacing，已跳过");
                    continue;
                }
                if (count < 1L) count = 1L;

                // 类型与权重（引用同一次解析出的类型表）
                JsonArray typeArr = GsonHelper.getAsJsonArray(o, "types");
                List<Integer> typeIdx = new ArrayList<>();
                List<Double> weights = new ArrayList<>();
                for (JsonElement te : typeArr) {
                    if (!te.isJsonObject()) continue;
                    JsonObject t = te.getAsJsonObject();
                    String typeId = GsonHelper.getAsString(t, "type");
                    double w = GsonHelper.getAsDouble(t, "weight", 1.0);
                    if (!(w > 0)) {
                        errors.add("环带 " + rl + " 中类型 " + typeId + " 权重必须 > 0，已忽略");
                        continue;
                    }
                    int gi = types.table.indexOf(normalizeTypeRef(typeId));
                    if (gi < 0) {
                        errors.add("环带 " + rl + " 引用了未定义的类型：" + typeId + "，已忽略");
                        continue;
                    }
                    typeIdx.add(gi);
                    weights.add(w);
                }
                if (typeIdx.isEmpty()) {
                    errors.add("环带 " + rl + " 没有任何有效类型，已跳过");
                    continue;
                }
                double totalW = 0;
                for (double w : weights) totalW += w;
                int[] idxArr = new int[typeIdx.size()];
                double[] cum = new double[typeIdx.size()];
                double acc = 0;
                for (int i = 0; i < typeIdx.size(); i++) {
                    idxArr[i] = typeIdx.get(i);
                    acc += weights.get(i) / totalW;
                    cum[i] = i == typeIdx.size() - 1 ? 1.0 : acc;
                }

                // 半径区间不得与已加载环带重叠
                boolean overlap = false;
                for (AsteroidBeltDef prev : out) {
                    if (inner < prev.outerRadius && outer > prev.innerRadius) {
                        overlap = true;
                        break;
                    }
                }
                if (overlap) {
                    errors.add("环带 " + rl + " 的半径区间与已加载环带重叠，已跳过");
                    continue;
                }

                long period = GsonHelper.getAsLong(o, "inner_orbit_period_ticks", 0L);
                out.add(new AsteroidBeltDef(name, inner, outer, iMin, iMax, ecc, count,
                        idxArr, cum, period));
            } catch (Exception ex) {
                errors.add("环带文件解析失败 " + rl + "：" + ex);
            }
        }
        return result;
    }

    private static double clamp01(double x) {
        return x < 0.0 ? 0.0 : (x > 1.0 ? 1.0 : x);
    }
}
