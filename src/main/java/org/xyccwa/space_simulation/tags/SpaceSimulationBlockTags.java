package org.xyccwa.space_simulation.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.xyccwa.space_simulation.SpaceSimulation;

public class SpaceSimulationBlockTags {

    public static final TagKey<Block> ORE_BLOCK_TAGS = create("ore_block");

    private static TagKey<Block> create(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(SpaceSimulation.MOD_ID, name));
    }
}
