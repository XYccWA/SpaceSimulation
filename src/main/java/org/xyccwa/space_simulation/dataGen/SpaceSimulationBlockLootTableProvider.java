package org.xyccwa.space_simulation.dataGen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;
import org.xyccwa.space_simulation.modItem.SpaceSimulationItem;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class SpaceSimulationBlockLootTableProvider extends BlockLootSubProvider {

    public SpaceSimulationBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {

//        dropSelf(SpaceSimulationBlock.NICKEL_IRON_ORE.get());

        add(SpaceSimulationBlock.DUST_BLOCK.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.DUST_BLOCK.get(),
                        SpaceSimulationItem.DUST.get(), 200F, 300F));

// ========== 金属矿石（10种） ==========

        add(SpaceSimulationBlock.CHALCOCITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.CARBONACEOUS_ORE.get(),
                        SpaceSimulationItem.CARBONACEOUS_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.KAMACITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.KAMACITE_ORE.get(),
                        SpaceSimulationItem.KAMACITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.TAENITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.TAENITE_ORE.get(),
                        SpaceSimulationItem.TAENITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.CHROMITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.CHROMITE_ORE.get(),
                        SpaceSimulationItem.CHROMITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.ILMENITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.ILMENITE_ORE.get(),
                        SpaceSimulationItem.ILMENITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.FORSTERITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.FORSTERITE_ORE.get(),
                        SpaceSimulationItem.FORSTERITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.WOLFRAMITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.WOLFRAMITE_ORE.get(),
                        SpaceSimulationItem.WOLFRAMITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.COLUMBITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.COLUMBITE_ORE.get(),
                        SpaceSimulationItem.COLUMBITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.MOLYBDENITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.MOLYBDENITE_ORE.get(),
                        SpaceSimulationItem.MOLYBDENITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.TANTALITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.TANTALITE_ORE.get(),
                        SpaceSimulationItem.TANTALITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.RHENIITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.RHENIITE_ORE.get(),
                        SpaceSimulationItem.RHENIITE_SAND.get(), 200F, 300F));


// ========== 硅质矿石（4种） ==========

        add(SpaceSimulationBlock.OLIVINE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.OLIVINE_ORE.get(),
                        SpaceSimulationItem.OLIVINE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.PYROXENE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.PYROXENE_ORE.get(),
                        SpaceSimulationItem.PYROXENE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.PLAGIOCLASE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.PLAGIOCLASE_ORE.get(),
                        SpaceSimulationItem.PLAGIOCLASE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.QUARTZ_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.QUARTZ_ORE.get(),
                        SpaceSimulationItem.QUARTZ_SAND.get(), 200F, 300F));


// ========== 碳质矿石（5种） ==========

        add(SpaceSimulationBlock.CARBONACEOUS_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.CARBONACEOUS_ORE.get(),
                        SpaceSimulationItem.CARBONACEOUS_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.PHYLLOSILICATE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.PHYLLOSILICATE_ORE.get(),
                        SpaceSimulationItem.PHYLLOSILICATE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.CARBONATE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.CARBONATE_ORE.get(),
                        SpaceSimulationItem.CARBONATE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.TROILITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.TROILITE_ORE.get(),
                        SpaceSimulationItem.TROILITE_SAND.get(), 200F, 300F));

        add(SpaceSimulationBlock.MAGNETITE_ORE.get(),
                block -> createLikeOreDrops(SpaceSimulationBlock.MAGNETITE_ORE.get(),
                        SpaceSimulationItem.MAGNETITE_SAND.get(), 200F, 300F));

    }

    protected LootTable.Builder createLikeOreDrops(Block block,Item item,Float min,Float max) {
        HolderLookup.RegistryLookup<Enchantment> registrylookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(
                block,
                (LootPoolEntryContainer.Builder)this.applyExplosionDecay(
                        block,
                        LootItem.lootTableItem(item)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)))
                                .apply(ApplyBonusCount.addOreBonusCount(registrylookup.getOrThrow(Enchantments.FORTUNE)))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return SpaceSimulationBlock.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}
