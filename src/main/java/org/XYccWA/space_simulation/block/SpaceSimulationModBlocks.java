package org.XYccWA.space_simulation.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.XYccWA.space_simulation.SpaceSimulationMod;
import org.XYccWA.space_simulation.fluid.ModFluids;

public class SpaceSimulationModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SpaceSimulationMod.MOD_ID);

    public static final RegistryObject<LiquidBlock> HYDROGRN_PEROXIDE_BLOCK = BLOCKS.register("hydrogrn_peroxide_block", () -> new LiquidBlock(ModFluids.SOURCE_HYDROGRN_PEROXIDE , BlockBehaviour.Properties.of().copy(Blocks.WATER)));
}
