package org.xyccwa.space_simulation.dataGen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.modItem.SpaceSimulationItem;

public class SpaceSimulationItemModelsProvider extends ItemModelProvider {
    public SpaceSimulationItemModelsProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, SpaceSimulation.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {

// ========== 浮土 ==========
        basicItem(SpaceSimulationItem.DUST.get());                      // 浮土

// ========== 矿砂 ==========
        basicItem(SpaceSimulationItem.CHALCOCITE_SAND.get());           // 辉铜矿砂
        basicItem(SpaceSimulationItem.KAMACITE_SAND.get());             // 铁纹石砂
        basicItem(SpaceSimulationItem.TAENITE_SAND.get());              // 镍纹石砂
        basicItem(SpaceSimulationItem.CHROMITE_SAND.get());             // 铬铁矿砂
        basicItem(SpaceSimulationItem.ILMENITE_SAND.get());             // 钛铁矿砂
        basicItem(SpaceSimulationItem.FORSTERITE_SAND.get());           // 镁橄榄石砂
        basicItem(SpaceSimulationItem.WOLFRAMITE_SAND.get());           // 钨锰铁矿砂
        basicItem(SpaceSimulationItem.COLUMBITE_SAND.get());            // 铌铁矿砂
        basicItem(SpaceSimulationItem.MOLYBDENITE_SAND.get());          // 辉钼矿砂
        basicItem(SpaceSimulationItem.TANTALITE_SAND.get());            // 钽铁矿砂
        basicItem(SpaceSimulationItem.RHENIITE_SAND.get());             // 辉铼矿砂
        basicItem(SpaceSimulationItem.OLIVINE_SAND.get());              // 橄榄石砂
        basicItem(SpaceSimulationItem.PYROXENE_SAND.get());             // 辉石砂
        basicItem(SpaceSimulationItem.PLAGIOCLASE_SAND.get());          // 斜长石砂
        basicItem(SpaceSimulationItem.QUARTZ_SAND.get());               // 石英砂
        basicItem(SpaceSimulationItem.CARBONACEOUS_SAND.get());         // 碳质球粒砂
        basicItem(SpaceSimulationItem.PHYLLOSILICATE_SAND.get());       // 层状硅酸盐砂
        basicItem(SpaceSimulationItem.CARBONATE_SAND.get());            // 碳酸盐砂
        basicItem(SpaceSimulationItem.TROILITE_SAND.get());             // 陨硫铁砂
        basicItem(SpaceSimulationItem.MAGNETITE_SAND.get());            // 磁铁矿砂
        basicItem(SpaceSimulationItem.COBALTITE_SAND.get());            // 辉砷钴矿砂
        basicItem(SpaceSimulationItem.SPODUMENE_SAND.get());            // 锂辉石砂
        basicItem(SpaceSimulationItem.ZIRCON_SAND.get());               // 锆石砂
        basicItem(SpaceSimulationItem.MONAZITE_SAND.get());             // 独居石砂
        basicItem(SpaceSimulationItem.URANINITE_SAND.get());            // 沥青铀矿砂
        basicItem(SpaceSimulationItem.THORITE_SAND.get());              // 钍石砂

// ========== 矿粉 ==========
        basicItem(SpaceSimulationItem.CHALCOCITE_POWDER.get());     // 辉铜矿粉
        basicItem(SpaceSimulationItem.KAMACITE_POWDER.get());       // 铁纹矿粉
        basicItem(SpaceSimulationItem.TAENITE_POWDER.get());        // 镍纹矿粉
        basicItem(SpaceSimulationItem.CHROMITE_POWDER.get());       // 铬铁矿粉
        basicItem(SpaceSimulationItem.ILMENITE_POWDER.get());       // 钛铁矿粉
        basicItem(SpaceSimulationItem.FORSTERITE_POWDER.get());     // 镁橄榄石粉
        basicItem(SpaceSimulationItem.WOLFRAMITE_POWDER.get());     // 钨锰矿粉
        basicItem(SpaceSimulationItem.COLUMBITE_POWDER.get());      // 铌铁矿粉
        basicItem(SpaceSimulationItem.MOLYBDENITE_POWDER.get());    // 辉钼矿粉
        basicItem(SpaceSimulationItem.TANTALITE_POWDER.get());      // 钽铁矿粉
        basicItem(SpaceSimulationItem.RHENIITE_POWDER.get());       // 辉铼矿粉
        basicItem(SpaceSimulationItem.OLIVINE_POWDER.get());        // 橄榄石粉
        basicItem(SpaceSimulationItem.PYROXENE_POWDER.get());       // 辉石粉
        basicItem(SpaceSimulationItem.PLAGIOCLASE_POWDER.get());    // 斜长石粉
        basicItem(SpaceSimulationItem.QUARTZ_POWDER.get());         // 石英粉
        basicItem(SpaceSimulationItem.CARBONACEOUS_POWDER.get());   // 碳质球粒粉
        basicItem(SpaceSimulationItem.PHYLLOSILICATE_POWDER.get()); // 层状硅酸盐粉
        basicItem(SpaceSimulationItem.CARBONATE_POWDER.get());      // 碳酸盐粉
        basicItem(SpaceSimulationItem.TROILITE_POWDER.get());       // 陨硫铁矿粉
        basicItem(SpaceSimulationItem.MAGNETITE_POWDER.get());      // 磁铁矿粉
        basicItem(SpaceSimulationItem.COBALTITE_POWDER.get());      // 辉砷钴矿粉
        basicItem(SpaceSimulationItem.SPODUMENE_POWDER.get());      // 锂辉石粉
        basicItem(SpaceSimulationItem.ZIRCON_POWDER.get());         // 锆石粉
        basicItem(SpaceSimulationItem.MONAZITE_POWDER.get());       // 独居石粉
        basicItem(SpaceSimulationItem.URANINITE_POWDER.get());      // 沥青铀矿粉
        basicItem(SpaceSimulationItem.THORITE_POWDER.get());        // 钍石粉
// ========== 金属单质锭 ==========
        basicItem(SpaceSimulationItem.COPPER_INGOT.get());              // 铜锭
        basicItem(SpaceSimulationItem.IRON_INGOT.get());                // 铁锭
        basicItem(SpaceSimulationItem.NICKEL_INGOT.get());              // 镍锭
        basicItem(SpaceSimulationItem.CHROMIUM_INGOT.get());            // 铬锭
        basicItem(SpaceSimulationItem.TITANIUM_INGOT.get());            // 钛锭
        basicItem(SpaceSimulationItem.MAGNESIUM_INGOT.get());           // 镁锭
        basicItem(SpaceSimulationItem.TUNGSTEN_INGOT.get());            // 钨锭
        basicItem(SpaceSimulationItem.NIOBIUM_INGOT.get());             // 铌锭
        basicItem(SpaceSimulationItem.MOLYBDENUM_INGOT.get());          // 钼锭
        basicItem(SpaceSimulationItem.TANTALUM_INGOT.get());            // 钽锭
        basicItem(SpaceSimulationItem.RHENIUM_INGOT.get());             // 铼锭
        basicItem(SpaceSimulationItem.PLATINUM_INGOT.get());            // 铂锭
        basicItem(SpaceSimulationItem.RHODIUM_INGOT.get());             // 铑锭

// ========== 合金锭 ==========
        basicItem(SpaceSimulationItem.IRON_NICKEL_ALLOY_INGOT.get());           // 铁镍合金锭
        basicItem(SpaceSimulationItem.CHROMIUM_NICKEL_IRON_ALLOY_INGOT.get());  // 铬镍铁合金锭
        basicItem(SpaceSimulationItem.TUNGSTEN_RHENIUM_ALLOY_INGOT.get());      // 钨铼合金锭
        basicItem(SpaceSimulationItem.NICKEL_RHENIUM_ALLOY_INGOT.get());        // 镍铼合金锭
        basicItem(SpaceSimulationItem.PLATINUM_RHODIUM_ALLOY_INGOT.get());      // 铂铑合金锭
        basicItem(SpaceSimulationItem.GH4061_ALLOY_INGOT.get());                // GH4061型合金锭
    }
}
