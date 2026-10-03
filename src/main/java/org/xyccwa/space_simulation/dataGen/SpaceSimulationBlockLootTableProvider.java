package org.xyccwa.space_simulation.dataGen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;

import java.util.Set;

/**
 * 方块战利品表：所有矿石方块（含浮土块）统一掉落自身 1 个。
 * （2026-10-02 用户裁定：不再直接掉矿砂，矿砂改由破碎/选矿工序产出。）
 */
public class SpaceSimulationBlockLootTableProvider extends BlockLootSubProvider {

    public SpaceSimulationBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {

// ========== 浮土块 ==========
        dropSelf(SpaceSimulationBlock.DUST_BLOCK.get());

// ========== 金属矿石（11种） ==========
        dropSelf(SpaceSimulationBlock.CHALCOCITE_ORE.get());
        dropSelf(SpaceSimulationBlock.KAMACITE_ORE.get());
        dropSelf(SpaceSimulationBlock.TAENITE_ORE.get());
        dropSelf(SpaceSimulationBlock.CHROMITE_ORE.get());
        dropSelf(SpaceSimulationBlock.ILMENITE_ORE.get());
        dropSelf(SpaceSimulationBlock.FORSTERITE_ORE.get());
        dropSelf(SpaceSimulationBlock.WOLFRAMITE_ORE.get());
        dropSelf(SpaceSimulationBlock.COLUMBITE_ORE.get());
        dropSelf(SpaceSimulationBlock.MOLYBDENITE_ORE.get());
        dropSelf(SpaceSimulationBlock.TANTALITE_ORE.get());
        dropSelf(SpaceSimulationBlock.RHENIITE_ORE.get());

// ========== 硅质矿石（4种） ==========
        dropSelf(SpaceSimulationBlock.OLIVINE_ORE.get());
        dropSelf(SpaceSimulationBlock.PYROXENE_ORE.get());
        dropSelf(SpaceSimulationBlock.PLAGIOCLASE_ORE.get());
        dropSelf(SpaceSimulationBlock.QUARTZ_ORE.get());

// ========== 碳质矿石（5种） ==========
        dropSelf(SpaceSimulationBlock.CARBONACEOUS_ORE.get());
        dropSelf(SpaceSimulationBlock.PHYLLOSILICATE_ORE.get());
        dropSelf(SpaceSimulationBlock.CARBONATE_ORE.get());
        dropSelf(SpaceSimulationBlock.TROILITE_ORE.get());
        dropSelf(SpaceSimulationBlock.MAGNETITE_ORE.get());

// ========== 新增矿石（6种） ==========
        dropSelf(SpaceSimulationBlock.COBALTITE_ORE.get());
        dropSelf(SpaceSimulationBlock.SPODUMENE_ORE.get());
        dropSelf(SpaceSimulationBlock.ZIRCON_ORE.get());
        dropSelf(SpaceSimulationBlock.MONAZITE_ORE.get());
        dropSelf(SpaceSimulationBlock.URANINITE_ORE.get());
        dropSelf(SpaceSimulationBlock.THORITE_ORE.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return SpaceSimulationBlock.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}
