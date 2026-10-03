package org.xyccwa.space_simulation.modItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;

import java.util.function.Supplier;

public class SpaceSimulationCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SpaceSimulation.MOD_ID);

    public static final Supplier<CreativeModeTab> SPACE_SIMULATION_ORE_TAB = CREATIVE_MODE_TABS.register("space_simulation_ore_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.space_simulation.ore.creative_tab"))
            .icon(() -> new ItemStack(Items.IRON_ORE))
            .displayItems((parameters, output) -> {
                // ========== 金属矿石 ==========
                output.accept(SpaceSimulationBlock.CHALCOCITE_ORE);       // 辉铜矿石
                output.accept(SpaceSimulationBlock.KAMACITE_ORE);         // 铁纹矿石
                output.accept(SpaceSimulationBlock.TAENITE_ORE);          // 镍纹矿石
                output.accept(SpaceSimulationBlock.CHROMITE_ORE);         // 铬铁矿石
                output.accept(SpaceSimulationBlock.ILMENITE_ORE);         // 钛铁矿石
                output.accept(SpaceSimulationBlock.FORSTERITE_ORE);       // 镁橄榄石矿石
                output.accept(SpaceSimulationBlock.WOLFRAMITE_ORE);       // 钨锰矿石
                output.accept(SpaceSimulationBlock.COLUMBITE_ORE);        // 铌铁矿石
                output.accept(SpaceSimulationBlock.MOLYBDENITE_ORE);      // 辉钼矿石
                output.accept(SpaceSimulationBlock.TANTALITE_ORE);        // 钽铁矿石
                output.accept(SpaceSimulationBlock.RHENIITE_ORE);         // 辉铼矿石
                output.accept(SpaceSimulationBlock.DUST_BLOCK);                 // 浮土

                // ========== 硅质矿石 ==========
                output.accept(SpaceSimulationBlock.OLIVINE_ORE);          // 橄榄石矿石
                output.accept(SpaceSimulationBlock.PYROXENE_ORE);         // 辉石矿石
                output.accept(SpaceSimulationBlock.PLAGIOCLASE_ORE);      // 斜长石矿石
                output.accept(SpaceSimulationBlock.QUARTZ_ORE);           // 石英矿石

                // ========== 碳质矿石 ==========
                output.accept(SpaceSimulationBlock.CARBONACEOUS_ORE);     // 碳质球粒矿石
                output.accept(SpaceSimulationBlock.PHYLLOSILICATE_ORE);   // 层状硅酸盐矿石
                output.accept(SpaceSimulationBlock.CARBONATE_ORE);        // 碳酸盐矿石
                output.accept(SpaceSimulationBlock.TROILITE_ORE);         // 陨硫铁矿石
                output.accept(SpaceSimulationBlock.MAGNETITE_ORE);        // 磁铁矿石

                // ========== 新增矿石（6种，科技树断链补齐） ==========
                output.accept(SpaceSimulationBlock.COBALTITE_ORE);        // 辉砷钴矿石
                output.accept(SpaceSimulationBlock.SPODUMENE_ORE);        // 锂辉石矿石
                output.accept(SpaceSimulationBlock.ZIRCON_ORE);           // 锆石矿石
                output.accept(SpaceSimulationBlock.MONAZITE_ORE);         // 独居石矿石
                output.accept(SpaceSimulationBlock.URANINITE_ORE);        // 沥青铀矿石
                output.accept(SpaceSimulationBlock.THORITE_ORE);          // 钍石矿石


                // ========== 金属矿砂（10种） ==========
                output.accept(SpaceSimulationItem.CHALCOCITE_SAND.get());      // 辉铜矿砂
                output.accept(SpaceSimulationItem.KAMACITE_SAND.get());        // 铁纹矿砂
                output.accept(SpaceSimulationItem.TAENITE_SAND.get());         // 镍纹矿砂
                output.accept(SpaceSimulationItem.CHROMITE_SAND.get());        // 铬铁矿砂
                output.accept(SpaceSimulationItem.ILMENITE_SAND.get());        // 钛铁矿砂
                output.accept(SpaceSimulationItem.FORSTERITE_SAND.get());      // 镁橄榄石砂
                output.accept(SpaceSimulationItem.WOLFRAMITE_SAND.get());      // 钨锰矿砂
                output.accept(SpaceSimulationItem.COLUMBITE_SAND.get());       // 铌铁矿砂
                output.accept(SpaceSimulationItem.MOLYBDENITE_SAND.get());     // 辉钼矿砂
                output.accept(SpaceSimulationItem.TANTALITE_SAND.get());       // 钽铁矿砂
                output.accept(SpaceSimulationItem.RHENIITE_SAND.get());        // 辉铼矿砂

                // ========== 硅质矿砂（4种） ==========
                output.accept(SpaceSimulationItem.OLIVINE_SAND.get());         // 橄榄石砂
                output.accept(SpaceSimulationItem.PYROXENE_SAND.get());        // 辉石砂
                output.accept(SpaceSimulationItem.PLAGIOCLASE_SAND.get());     // 斜长石砂
                output.accept(SpaceSimulationItem.QUARTZ_SAND.get());          // 石英砂

                // ========== 碳质矿砂（5种） ==========
                output.accept(SpaceSimulationItem.CARBONACEOUS_SAND.get());    // 碳质球粒砂
                output.accept(SpaceSimulationItem.PHYLLOSILICATE_SAND.get());  // 层状硅酸盐砂
                output.accept(SpaceSimulationItem.CARBONATE_SAND.get());       // 碳酸盐砂
                output.accept(SpaceSimulationItem.TROILITE_SAND.get());        // 陨硫铁矿砂
                output.accept(SpaceSimulationItem.MAGNETITE_SAND.get());       // 磁铁矿砂

                // ========== 新增矿砂（6种，科技树断链补齐） ==========
                output.accept(SpaceSimulationItem.COBALTITE_SAND.get());       // 辉砷钴矿砂
                output.accept(SpaceSimulationItem.SPODUMENE_SAND.get());       // 锂辉石砂
                output.accept(SpaceSimulationItem.ZIRCON_SAND.get());          // 锆石砂
                output.accept(SpaceSimulationItem.MONAZITE_SAND.get());        // 独居石砂
                output.accept(SpaceSimulationItem.URANINITE_SAND.get());       // 沥青铀矿砂
                output.accept(SpaceSimulationItem.THORITE_SAND.get());         // 钍石砂


                //风化层
                output.accept(SpaceSimulationItem.DUST.get());                 // 浮土

                // ========== 矿粉（矿砂研磨产物） ==========
                output.accept(SpaceSimulationItem.CHALCOCITE_POWDER.get());     // 辉铜矿粉
                output.accept(SpaceSimulationItem.KAMACITE_POWDER.get());       // 铁纹矿粉
                output.accept(SpaceSimulationItem.TAENITE_POWDER.get());        // 镍纹矿粉
                output.accept(SpaceSimulationItem.CHROMITE_POWDER.get());       // 铬铁矿粉
                output.accept(SpaceSimulationItem.ILMENITE_POWDER.get());       // 钛铁矿粉
                output.accept(SpaceSimulationItem.FORSTERITE_POWDER.get());     // 镁橄榄石粉
                output.accept(SpaceSimulationItem.WOLFRAMITE_POWDER.get());     // 钨锰矿粉
                output.accept(SpaceSimulationItem.COLUMBITE_POWDER.get());      // 铌铁矿粉
                output.accept(SpaceSimulationItem.MOLYBDENITE_POWDER.get());    // 辉钼矿粉
                output.accept(SpaceSimulationItem.TANTALITE_POWDER.get());      // 钽铁矿粉
                output.accept(SpaceSimulationItem.RHENIITE_POWDER.get());       // 辉铼矿粉
                output.accept(SpaceSimulationItem.OLIVINE_POWDER.get());        // 橄榄石粉
                output.accept(SpaceSimulationItem.PYROXENE_POWDER.get());       // 辉石粉
                output.accept(SpaceSimulationItem.PLAGIOCLASE_POWDER.get());    // 斜长石粉
                output.accept(SpaceSimulationItem.QUARTZ_POWDER.get());         // 石英粉
                output.accept(SpaceSimulationItem.CARBONACEOUS_POWDER.get());   // 碳质球粒粉
                output.accept(SpaceSimulationItem.PHYLLOSILICATE_POWDER.get()); // 层状硅酸盐粉
                output.accept(SpaceSimulationItem.CARBONATE_POWDER.get());      // 碳酸盐粉
                output.accept(SpaceSimulationItem.TROILITE_POWDER.get());       // 陨硫铁矿粉
                output.accept(SpaceSimulationItem.MAGNETITE_POWDER.get());      // 磁铁矿粉
                output.accept(SpaceSimulationItem.COBALTITE_POWDER.get());      // 辉砷钴矿粉
                output.accept(SpaceSimulationItem.SPODUMENE_POWDER.get());      // 锂辉石粉
                output.accept(SpaceSimulationItem.ZIRCON_POWDER.get());         // 锆石粉
                output.accept(SpaceSimulationItem.MONAZITE_POWDER.get());       // 独居石粉
                output.accept(SpaceSimulationItem.URANINITE_POWDER.get());      // 沥青铀矿粉
                output.accept(SpaceSimulationItem.THORITE_POWDER.get());        // 钍石粉
            })
            .build());

    public static final Supplier<CreativeModeTab> SPACE_SIMULATION_METAL_TAB = CREATIVE_MODE_TABS.register("space_simulation_metal_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.space_simulation.metal.creative_tab"))
            .icon(() -> new ItemStack(Items.IRON_ORE))
            .displayItems((parameters, output) -> {
                // ========== 金属单质锭 ==========
                output.accept(SpaceSimulationItem.COPPER_INGOT.get());         // 铜锭
                output.accept(SpaceSimulationItem.IRON_INGOT.get());           // 铁锭
                output.accept(SpaceSimulationItem.NICKEL_INGOT.get());         // 镍锭
                output.accept(SpaceSimulationItem.CHROMIUM_INGOT.get());       // 铬锭
                output.accept(SpaceSimulationItem.TITANIUM_INGOT.get());       // 钛锭
                output.accept(SpaceSimulationItem.MAGNESIUM_INGOT.get());      // 镁锭
                output.accept(SpaceSimulationItem.TUNGSTEN_INGOT.get());       // 钨锭
                output.accept(SpaceSimulationItem.NIOBIUM_INGOT.get());        // 铌锭
                output.accept(SpaceSimulationItem.MOLYBDENUM_INGOT.get());     // 钼锭
                output.accept(SpaceSimulationItem.TANTALUM_INGOT.get());       // 钽锭
                output.accept(SpaceSimulationItem.RHENIUM_INGOT.get());        // 铼锭
                output.accept(SpaceSimulationItem.PLATINUM_INGOT.get());       // 铂锭
                output.accept(SpaceSimulationItem.RHODIUM_INGOT.get());        // 铑锭

                // ========== 合金锭 ==========
                output.accept(SpaceSimulationItem.IRON_NICKEL_ALLOY_INGOT.get());              // 铁镍合金锭
                output.accept(SpaceSimulationItem.CHROMIUM_NICKEL_IRON_ALLOY_INGOT.get());     // 铬镍铁合金锭
                output.accept(SpaceSimulationItem.TUNGSTEN_RHENIUM_ALLOY_INGOT.get());         // 钨铼合金锭
                output.accept(SpaceSimulationItem.NICKEL_RHENIUM_ALLOY_INGOT.get());           // 镍铼合金锭
                output.accept(SpaceSimulationItem.PLATINUM_RHODIUM_ALLOY_INGOT.get());         // 铂铑合金锭
                output.accept(SpaceSimulationItem.GH4061_ALLOY_INGOT.get());                   // GH4061型合金锭
            })
            .build());
}
