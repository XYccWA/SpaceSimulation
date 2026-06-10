package org.xyccwa.space_simulation;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.xyccwa.space_simulation.dataGen.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = SpaceSimulation.MOD_ID)
public class SpaceSimulationDataGenerator {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event){
        DataGenerator generator =event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new SpaceSimulationRecipesProvider(packOutput, lookupProvider));

        BlockTagsProvider blockTagsProvider = new SpaceSimulationBlockTagsProvider(packOutput, lookupProvider, existingFileHelper);
        generator.addProvider(event.includeServer(), blockTagsProvider);
        generator.addProvider(event.includeServer(),new SpaceSimulationItemTagsProvider(packOutput, lookupProvider, blockTagsProvider.contentsGetter(),existingFileHelper));

        generator.addProvider(event.includeClient(), new SpaceSimulationItemModelsProvider(packOutput, existingFileHelper));
        generator.addProvider(event.includeClient(), new SpaceSimulationBlockStateProvider(packOutput, existingFileHelper));
        generator.addProvider(event.includeClient(), new SpaceSimulationEnUsLangProvider(packOutput));
        generator.addProvider(event.includeClient(), new SpaceSimulationZhCnLangProvider(packOutput));

        generator.addProvider(event.includeServer(),new LootTableProvider(packOutput, Collections.emptySet(),
                List.of(new LootTableProvider.SubProviderEntry(SpaceSimulationBlockLootTableProvider::new, LootContextParamSets.BLOCK)),lookupProvider));

    }
}
