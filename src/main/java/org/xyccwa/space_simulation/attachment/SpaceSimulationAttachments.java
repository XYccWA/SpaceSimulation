package org.xyccwa.space_simulation.attachment;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.xyccwa.space_simulation.SpaceSimulation;

import java.util.function.Supplier;

public class SpaceSimulationAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SpaceSimulation.MOD_ID);

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}