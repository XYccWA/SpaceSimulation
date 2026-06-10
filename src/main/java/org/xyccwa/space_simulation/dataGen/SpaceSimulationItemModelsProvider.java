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
