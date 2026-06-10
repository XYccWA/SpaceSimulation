package org.xyccwa.space_simulation.modItem;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.xyccwa.space_simulation.SpaceSimulation;

public class SpaceSimulationItem {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SpaceSimulation.MOD_ID);

    // ========== 浮土 ==========
    public static final DeferredItem<Item> DUST =
            ITEMS.register("dust", () -> new Item(new Item.Properties().stacksTo(1000)));

// ========== 矿砂 ==========
    // ========== 金属矿砂 ==========
    public static final DeferredItem<Item> CHALCOCITE_SAND =
            ITEMS.register("chalcocite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> KAMACITE_SAND =
            ITEMS.register("kamacite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> TAENITE_SAND =
            ITEMS.register("taenite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> CHROMITE_SAND =
            ITEMS.register("chromite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> ILMENITE_SAND =
            ITEMS.register("ilmenite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> FORSTERITE_SAND =
            ITEMS.register("forsterite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> WOLFRAMITE_SAND =
            ITEMS.register("wolframite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> COLUMBITE_SAND =
            ITEMS.register("columbite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> MOLYBDENITE_SAND =
            ITEMS.register("molybdenite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> TANTALITE_SAND =
            ITEMS.register("tantalite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> RHENIITE_SAND =
            ITEMS.register("rheniite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));

    // ========== 硅质矿砂 ==========
    public static final DeferredItem<Item> OLIVINE_SAND =
            ITEMS.register("olivine_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> PYROXENE_SAND =
            ITEMS.register("pyroxene_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> PLAGIOCLASE_SAND =
            ITEMS.register("plagioclase_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> QUARTZ_SAND =
            ITEMS.register("quartz_sand", () -> new Item(new Item.Properties().stacksTo(1000)));

    // ========== 碳质矿砂 ==========
    public static final DeferredItem<Item> CARBONACEOUS_SAND =
            ITEMS.register("carbonaceous_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> PHYLLOSILICATE_SAND =
            ITEMS.register("phyllosilicate_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> CARBONATE_SAND =
            ITEMS.register("carbonate_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> TROILITE_SAND =
            ITEMS.register("troilite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final DeferredItem<Item> MAGNETITE_SAND =
            ITEMS.register("magnetite_sand", () -> new Item(new Item.Properties().stacksTo(1000)));




// ========== 金属单质锭 ==========
    /** 铜锭 */
    public static final DeferredItem<Item> COPPER_INGOT =
            ITEMS.register("copper_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 铁锭 - 来自铁纹矿/镍纹矿，基础结构金属，熔点1538℃ */
    public static final DeferredItem<Item> IRON_INGOT =
            ITEMS.register("iron_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 镍锭 - 来自铁纹矿/镍纹矿，抗腐蚀性强，常用于合金与镀层，熔点1455℃ */
    public static final DeferredItem<Item> NICKEL_INGOT =
            ITEMS.register("nickel_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 铬锭 - 来自陨铬铁矿/铬铁矿，极硬且耐高温，熔点1907℃ */
    public static final DeferredItem<Item> CHROMIUM_INGOT =
            ITEMS.register("chromium_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 钛锭 - 来自钛铁矿，轻质高强度，广泛用于航天结构件，熔点1668℃ */
    public static final DeferredItem<Item> TITANIUM_INGOT =
            ITEMS.register("titanium_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 镁锭 - 来自镁橄榄石，轻金属，遇水反应，熔点650℃ */
    public static final DeferredItem<Item> MAGNESIUM_INGOT =
            ITEMS.register("magnesium_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 钨锭 - 来自钨锰矿/钨铁矿，熔点最高的金属（3412℃），用于火箭喷管 */
    public static final DeferredItem<Item> TUNGSTEN_INGOT =
            ITEMS.register("tungsten_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 铌锭 - 来自铌铁矿-钽铁矿，熔点2477℃，用于火箭喷嘴与热防护系统 */
    public static final DeferredItem<Item> NIOBIUM_INGOT =
            ITEMS.register("niobium_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 钼锭 - 来自辉钼矿，熔点2623℃，用于发动机支架与涡轮盘 */
    public static final DeferredItem<Item> MOLYBDENUM_INGOT =
            ITEMS.register("molybdenum_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 钽锭 - 来自钽铁矿，熔点3017℃，用于火箭发动机热端部件 */
    public static final DeferredItem<Item> TANTALUM_INGOT =
            ITEMS.register("tantalum_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 铼锭 - 来自辉铼矿，熔点3180℃，用于高温合金，提升钨/镍合金塑性 */
    public static final DeferredItem<Item> RHENIUM_INGOT =
            ITEMS.register("rhenium_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 铂锭 - 来自铂族矿物，熔点1768℃，抗高温氧化，用于催化剂与喷管涂层 */
    public static final DeferredItem<Item> PLATINUM_INGOT =
            ITEMS.register("platinum_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 铑锭 - 来自铂族矿物，熔点1963℃，高温合金添加剂，提高抗腐蚀性 */
    public static final DeferredItem<Item> RHODIUM_INGOT =
            ITEMS.register("rhodium_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

// ========== 合金锭 ==========

    /** 铁镍合金锭 - 铁+镍天然合金，模拟铁纹石成分，基础结构材料 */
    public static final DeferredItem<Item> IRON_NICKEL_ALLOY_INGOT =
            ITEMS.register("iron_nickel_alloy_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 铬镍铁合金锭 - 铁+镍+铬，Incoloy 890型，发动机涡轮泵壳体，耐750℃高温 */
    public static final DeferredItem<Item> CHROMIUM_NICKEL_IRON_ALLOY_INGOT =
            ITEMS.register("chromium_nickel_iron_alloy_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 钨铼合金锭 - 钨+铼，火箭喷管喉衬，承受超2000℃燃气冲刷 */
    public static final DeferredItem<Item> TUNGSTEN_RHENIUM_ALLOY_INGOT =
            ITEMS.register("tungsten_rhenium_alloy_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 镍铼合金锭 - 镍+铼，涡轮叶片，提升650-850℃区间力学性能 */
    public static final DeferredItem<Item> NICKEL_RHENIUM_ALLOY_INGOT =
            ITEMS.register("nickel_rhenium_alloy_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** 铂铑合金锭 - 铂+铑，发动机喷管，耐1500-1600℃富氧烧蚀 */
    public static final DeferredItem<Item> PLATINUM_RHODIUM_ALLOY_INGOT =
            ITEMS.register("platinum_rhodium_alloy_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    /** GH4061型合金锭 - 铁镍铬基+钴钨钼，大推力发动机涡轮球壳，抗富氧烧蚀55MPa */
    public static final DeferredItem<Item> GH4061_ALLOY_INGOT =
            ITEMS.register("gh4061_alloy_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));


    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
