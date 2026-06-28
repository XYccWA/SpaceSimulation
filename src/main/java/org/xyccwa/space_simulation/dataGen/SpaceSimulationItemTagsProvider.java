package org.xyccwa.space_simulation.dataGen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.modItem.SpaceSimulationItem;
import org.xyccwa.space_simulation.tags.SpaceSimulationItemTags;

import java.util.concurrent.CompletableFuture;

public class SpaceSimulationItemTagsProvider extends ItemTagsProvider {
    public SpaceSimulationItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, SpaceSimulation.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(SpaceSimulationItemTags.ORE_ITEM_TAGS)
                .add(SpaceSimulationItem.KAMACITE_SAND.get())
                .add(SpaceSimulationItem.TAENITE_SAND.get())
                .add(SpaceSimulationItem.CHROMITE_SAND.get())
                .add(SpaceSimulationItem.ILMENITE_SAND.get())
                .add(SpaceSimulationItem.FORSTERITE_SAND.get())
                .add(SpaceSimulationItem.WOLFRAMITE_SAND.get())
                .add(SpaceSimulationItem.COLUMBITE_SAND.get())
                .add(SpaceSimulationItem.MOLYBDENITE_SAND.get())
                .add(SpaceSimulationItem.TANTALITE_SAND.get())
                .add(SpaceSimulationItem.RHENIITE_SAND.get())
                .add(SpaceSimulationItem.OLIVINE_SAND.get())
                .add(SpaceSimulationItem.PYROXENE_SAND.get())
                .add(SpaceSimulationItem.PLAGIOCLASE_SAND.get())
                .add(SpaceSimulationItem.QUARTZ_SAND.get())
                .add(SpaceSimulationItem.CARBONACEOUS_SAND.get())
                .add(SpaceSimulationItem.PHYLLOSILICATE_SAND.get())
                .add(SpaceSimulationItem.CARBONATE_SAND.get())
                .add(SpaceSimulationItem.TROILITE_SAND.get())
                .add(SpaceSimulationItem.MAGNETITE_SAND.get());
    }
}
