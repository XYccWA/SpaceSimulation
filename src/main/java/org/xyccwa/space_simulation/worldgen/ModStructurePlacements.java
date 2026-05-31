package org.xyccwa.space_simulation.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.worldgen.RingStructurePlacement;

import java.util.function.Supplier;

public class ModStructurePlacements {
    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, SpaceSimulation.MOD_ID);

    public static final Supplier<StructurePlacementType<RingStructurePlacement>> RING_PLACEMENT =
            STRUCTURE_PLACEMENTS.register("ring_placement",
                    () -> () -> RingStructurePlacement.CODEC);

    public static void register(IEventBus bus) {
        STRUCTURE_PLACEMENTS.register(bus);
    }
}