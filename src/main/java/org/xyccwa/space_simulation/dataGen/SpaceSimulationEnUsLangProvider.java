package org.xyccwa.space_simulation.dataGen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;
import org.xyccwa.space_simulation.modItem.SpaceSimulationItem;

public class SpaceSimulationEnUsLangProvider extends LanguageProvider {
    public SpaceSimulationEnUsLangProvider(PackOutput output) {
        super(output, SpaceSimulation.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {

        add(SpaceSimulationBlock.DUST_BLOCK.get(), "Dust");

// ========== 金属矿石 ==========
        add(SpaceSimulationBlock.CHALCOCITE_ORE.get(), "Chalcocite Ore");
        add(SpaceSimulationBlock.KAMACITE_ORE.get(), "Kamacite Ore");
        add(SpaceSimulationBlock.TAENITE_ORE.get(), "Taenite Ore");
        add(SpaceSimulationBlock.CHROMITE_ORE.get(), "Chromite Ore");
        add(SpaceSimulationBlock.ILMENITE_ORE.get(), "Ilmenite Ore");
        add(SpaceSimulationBlock.FORSTERITE_ORE.get(), "Forsterite Ore");
        add(SpaceSimulationBlock.WOLFRAMITE_ORE.get(), "Wolframite Ore");
        add(SpaceSimulationBlock.COLUMBITE_ORE.get(), "Columbite Ore");
        add(SpaceSimulationBlock.MOLYBDENITE_ORE.get(), "Molybdenite Ore");
        add(SpaceSimulationBlock.TANTALITE_ORE.get(), "Tantalite Ore");
        add(SpaceSimulationBlock.RHENIITE_ORE.get(), "Rheniite Ore");

// ========== 硅质矿石 ==========
        add(SpaceSimulationBlock.OLIVINE_ORE.get(), "Olivine Ore");
        add(SpaceSimulationBlock.PYROXENE_ORE.get(), "Pyroxene Ore");
        add(SpaceSimulationBlock.PLAGIOCLASE_ORE.get(), "Plagioclase Ore");
        add(SpaceSimulationBlock.QUARTZ_ORE.get(), "Quartz Ore");

// ========== 碳质矿石 ==========
        add(SpaceSimulationBlock.CARBONACEOUS_ORE.get(), "Carbonaceous Ore");
        add(SpaceSimulationBlock.PHYLLOSILICATE_ORE.get(), "Phyllosilicate Ore");
        add(SpaceSimulationBlock.CARBONATE_ORE.get(), "Carbonate Ore");
        add(SpaceSimulationBlock.TROILITE_ORE.get(), "Troilite Ore");
        add(SpaceSimulationBlock.MAGNETITE_ORE.get(), "Magnetite Ore");

        add(SpaceSimulationItem.DUST.get(), "DUST");
// 金属矿砂
        add(SpaceSimulationItem.CHALCOCITE_SAND.get(), "Chalcocite Sand");
        add(SpaceSimulationItem.KAMACITE_SAND.get(), "Kamacite Sand");
        add(SpaceSimulationItem.TAENITE_SAND.get(), "Taenite Sand");
        add(SpaceSimulationItem.CHROMITE_SAND.get(), "Chromite Sand");
        add(SpaceSimulationItem.ILMENITE_SAND.get(), "Ilmenite Sand");
        add(SpaceSimulationItem.FORSTERITE_SAND.get(), "Forsterite Sand");
        add(SpaceSimulationItem.WOLFRAMITE_SAND.get(), "Wolframite Sand");
        add(SpaceSimulationItem.COLUMBITE_SAND.get(), "Columbite Sand");
        add(SpaceSimulationItem.MOLYBDENITE_SAND.get(), "Molybdenite Sand");
        add(SpaceSimulationItem.TANTALITE_SAND.get(), "Tantalite Sand");
        add(SpaceSimulationItem.RHENIITE_SAND.get(), "Rheniite Sand");

// 硅质矿砂
        add(SpaceSimulationItem.OLIVINE_SAND.get(), "Olivine Sand");
        add(SpaceSimulationItem.PYROXENE_SAND.get(), "Pyroxene Sand");
        add(SpaceSimulationItem.PLAGIOCLASE_SAND.get(), "Plagioclase Sand");
        add(SpaceSimulationItem.QUARTZ_SAND.get(), "Quartz Sand");

// 碳质矿砂
        add(SpaceSimulationItem.CARBONACEOUS_SAND.get(), "Carbonaceous Sand");
        add(SpaceSimulationItem.PHYLLOSILICATE_SAND.get(), "Phyllosilicate Sand");
        add(SpaceSimulationItem.CARBONATE_SAND.get(), "Carbonate Sand");
        add(SpaceSimulationItem.TROILITE_SAND.get(), "Troilite Sand");
        add(SpaceSimulationItem.MAGNETITE_SAND.get(), "Magnetite Sand");

// ========== 金属单质锭 ==========
        add(SpaceSimulationItem.COPPER_INGOT.get(), "Copper Ingot");
        add(SpaceSimulationItem.IRON_INGOT.get(), "Iron Ingot");
        add(SpaceSimulationItem.NICKEL_INGOT.get(), "Nickel Ingot");
        add(SpaceSimulationItem.CHROMIUM_INGOT.get(), "Chromium Ingot");
        add(SpaceSimulationItem.TITANIUM_INGOT.get(), "Titanium Ingot");
        add(SpaceSimulationItem.MAGNESIUM_INGOT.get(), "Magnesium Ingot");
        add(SpaceSimulationItem.TUNGSTEN_INGOT.get(), "Tungsten Ingot");
        add(SpaceSimulationItem.NIOBIUM_INGOT.get(), "Niobium Ingot");
        add(SpaceSimulationItem.MOLYBDENUM_INGOT.get(), "Molybdenum Ingot");
        add(SpaceSimulationItem.TANTALUM_INGOT.get(), "Tantalum Ingot");
        add(SpaceSimulationItem.RHENIUM_INGOT.get(), "Rhenium Ingot");
        add(SpaceSimulationItem.PLATINUM_INGOT.get(), "Platinum Ingot");
        add(SpaceSimulationItem.RHODIUM_INGOT.get(), "Rhodium Ingot");

// ========== 合金锭 ==========
        add(SpaceSimulationItem.IRON_NICKEL_ALLOY_INGOT.get(), "Iron-Nickel Alloy Ingot");
        add(SpaceSimulationItem.CHROMIUM_NICKEL_IRON_ALLOY_INGOT.get(), "Chromium-Nickel-Iron Alloy Ingot");
        add(SpaceSimulationItem.TUNGSTEN_RHENIUM_ALLOY_INGOT.get(), "Tungsten-Rhenium Alloy Ingot");
        add(SpaceSimulationItem.NICKEL_RHENIUM_ALLOY_INGOT.get(), "Nickel-Rhenium Alloy Ingot");
        add(SpaceSimulationItem.PLATINUM_RHODIUM_ALLOY_INGOT.get(), "Platinum-Rhodium Alloy Ingot");
        add(SpaceSimulationItem.GH4061_ALLOY_INGOT.get(), "GH4061 Alloy Ingot");


        add("itemGroup.space_simulation.creative_tab","Space Simulation");

        addDeathMessage("high_g_force", "%s was crushed by extreme G-forces");
        addDeathMessage("high_g_force.player", "%s was crushed by extreme G-forces while trying to escape %s");
        addDeathMessage("sustained_g_force", "%s succumbed to sustained high G-forces");
        addDeathMessage("sustained_g_force.player", "%s succumbed to sustained high G-forces while fighting %s");

    }

    private void addDamageType(String key, String value) {
        add("damage_type." + SpaceSimulation.MOD_ID + "." + key, value);
    }

    private void addDeathMessage(String key, String value) {
        add("death.attack." + key, value);
    }
}
