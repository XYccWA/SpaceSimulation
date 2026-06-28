package org.xyccwa.space_simulation.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.xyccwa.space_simulation.SpaceSimulation;

public class SpaceSimulationItemTags {

    public static final TagKey<Item> ORE_ITEM_TAGS = create("ore_item");

    private static TagKey<Item> create(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(SpaceSimulation.MOD_ID, name));
    }
}
