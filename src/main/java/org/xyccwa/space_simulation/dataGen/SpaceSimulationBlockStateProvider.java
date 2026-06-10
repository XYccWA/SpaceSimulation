package org.xyccwa.space_simulation.dataGen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;

public class SpaceSimulationBlockStateProvider extends BlockStateProvider {
    public SpaceSimulationBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, SpaceSimulation.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {

        simpleBlockWithItem(SpaceSimulationBlock.DUST_BLOCK.get(),
                cubeAll(SpaceSimulationBlock.DUST_BLOCK.get()));

// ========== 金属矿石（10种） ==========
//辉铜矿石
        simpleBlockWithItem(SpaceSimulationBlock.CHALCOCITE_ORE.get(),
            cubeAll(SpaceSimulationBlock.CHALCOCITE_ORE.get()));

// 铁纹矿石
        simpleBlockWithItem(SpaceSimulationBlock.KAMACITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.KAMACITE_ORE.get()));

// 镍纹矿石
        simpleBlockWithItem(SpaceSimulationBlock.TAENITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.TAENITE_ORE.get()));

// 铬铁矿石
        simpleBlockWithItem(SpaceSimulationBlock.CHROMITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.CHROMITE_ORE.get()));

// 钛铁矿石
        simpleBlockWithItem(SpaceSimulationBlock.ILMENITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.ILMENITE_ORE.get()));

// 镁橄榄石矿石
        simpleBlockWithItem(SpaceSimulationBlock.FORSTERITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.FORSTERITE_ORE.get()));

// 钨锰矿石
        simpleBlockWithItem(SpaceSimulationBlock.WOLFRAMITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.WOLFRAMITE_ORE.get()));

// 铌铁矿石
        simpleBlockWithItem(SpaceSimulationBlock.COLUMBITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.COLUMBITE_ORE.get()));

// 辉钼矿石
        simpleBlockWithItem(SpaceSimulationBlock.MOLYBDENITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.MOLYBDENITE_ORE.get()));

// 钽铁矿石
        simpleBlockWithItem(SpaceSimulationBlock.TANTALITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.TANTALITE_ORE.get()));

// 辉铼矿石
        simpleBlockWithItem(SpaceSimulationBlock.RHENIITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.RHENIITE_ORE.get()));


// ========== 硅质矿石（4种） ==========

// 橄榄石矿石
        simpleBlockWithItem(SpaceSimulationBlock.OLIVINE_ORE.get(),
                cubeAll(SpaceSimulationBlock.OLIVINE_ORE.get()));

// 辉石矿石
        simpleBlockWithItem(SpaceSimulationBlock.PYROXENE_ORE.get(),
                cubeAll(SpaceSimulationBlock.PYROXENE_ORE.get()));

// 斜长石矿石
        simpleBlockWithItem(SpaceSimulationBlock.PLAGIOCLASE_ORE.get(),
                cubeAll(SpaceSimulationBlock.PLAGIOCLASE_ORE.get()));

// 石英矿石
        simpleBlockWithItem(SpaceSimulationBlock.QUARTZ_ORE.get(),
                cubeAll(SpaceSimulationBlock.QUARTZ_ORE.get()));


// ========== 碳质矿石（5种） ==========

// 碳质球粒矿石
        simpleBlockWithItem(SpaceSimulationBlock.CARBONACEOUS_ORE.get(),
                cubeAll(SpaceSimulationBlock.CARBONACEOUS_ORE.get()));

// 层状硅酸盐矿石
        simpleBlockWithItem(SpaceSimulationBlock.PHYLLOSILICATE_ORE.get(),
                cubeAll(SpaceSimulationBlock.PHYLLOSILICATE_ORE.get()));

// 碳酸盐矿石
        simpleBlockWithItem(SpaceSimulationBlock.CARBONATE_ORE.get(),
                cubeAll(SpaceSimulationBlock.CARBONATE_ORE.get()));

// 陨硫铁矿石
        simpleBlockWithItem(SpaceSimulationBlock.TROILITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.TROILITE_ORE.get()));

// 磁铁矿石
        simpleBlockWithItem(SpaceSimulationBlock.MAGNETITE_ORE.get(),
                cubeAll(SpaceSimulationBlock.MAGNETITE_ORE.get()));
    }
}
