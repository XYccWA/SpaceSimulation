package org.xyccwa.space_simulation.modItem;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.xyccwa.space_simulation.SpaceSimulation;

public class SpaceSimulationItem {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SpaceSimulation.MOD_ID);

    public static final DeferredItem<Item> EXAMPLE = ITEMS.register("example", () -> new Item(new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
