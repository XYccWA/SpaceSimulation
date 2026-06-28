package org.xyccwa.space_simulation.dataGen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;
import org.xyccwa.space_simulation.tags.SpaceSimulationBlockTags;

import java.util.concurrent.CompletableFuture;

public class SpaceSimulationBlockTagsProvider extends BlockTagsProvider {
    public SpaceSimulationBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, SpaceSimulation.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {

        tag(SpaceSimulationBlockTags.ORE_BLOCK_TAGS)
                .add(SpaceSimulationBlock.KAMACITE_ORE.get())
                .add(SpaceSimulationBlock.TAENITE_ORE.get())
                .add(SpaceSimulationBlock.CHROMITE_ORE.get())
                .add(SpaceSimulationBlock.ILMENITE_ORE.get())
                .add(SpaceSimulationBlock.FORSTERITE_ORE.get())
                .add(SpaceSimulationBlock.WOLFRAMITE_ORE.get())
                .add(SpaceSimulationBlock.COLUMBITE_ORE.get())
                .add(SpaceSimulationBlock.MOLYBDENITE_ORE.get())
                .add(SpaceSimulationBlock.TANTALITE_ORE.get())
                .add(SpaceSimulationBlock.RHENIITE_ORE.get())
                .add(SpaceSimulationBlock.OLIVINE_ORE.get())
                .add(SpaceSimulationBlock.PYROXENE_ORE.get())
                .add(SpaceSimulationBlock.PLAGIOCLASE_ORE.get())
                .add(SpaceSimulationBlock.QUARTZ_ORE.get())
                .add(SpaceSimulationBlock.CARBONACEOUS_ORE.get())
                .add(SpaceSimulationBlock.PHYLLOSILICATE_ORE.get())
                .add(SpaceSimulationBlock.CARBONATE_ORE.get())
                .add(SpaceSimulationBlock.TROILITE_ORE.get())
                .add(SpaceSimulationBlock.MAGNETITE_ORE.get());
    }
}
