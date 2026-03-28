package org.XYccWA.space_simulation.fluid;

import org.XYccWA.space_simulation.SpaceSimulationMod;
import org.XYccWA.space_simulation.block.SpaceSimulationModBlocks;
import org.XYccWA.space_simulation.fluid.ModFluidTypes;
import org.XYccWA.space_simulation.item.SpaceSimulationModItems;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 之后我们书写自己的流体的类，在流体中，有流体和流体类型，这两个类都需要写。
 * 先写这里的流体的类吧
 *
 */
public class ModFluids {
    // DeferredRegister对象，这次的注册的是流体。
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, SpaceSimulationMod.MOD_ID);
    // 静态流体，源头
    public static final RegistryObject<FlowingFluid> SOURCE_HYDROGRN_PEROXIDE = FLUIDS.register("hydrogen_peroxide_fluid",
            () -> new ForgeFlowingFluid.Source(ModFluids.SOAP_WATER_FLUID_PROPERTIES));
    // 动态流体，传入的都是这个properties流体的配置
    public static final RegistryObject<FlowingFluid> FLOWING_HYDROGRN_PEROXIDE = FLUIDS.register("flowing_hydrogen_peroxide",
            () -> new ForgeFlowingFluid.Flowing(ModFluids.SOAP_WATER_FLUID_PROPERTIES));

    // 这里是properties。new 出来就可以
    // 传入的分别是流体的type，这个type等会写，以及上面的源头，和动态的流体，设置流体的流动的最远距离以及每个tick流淌出去的方块。
    // 设置对应的item的流体捅，这个捅等会在item类下添加。流体方块在block类中添加。
    public static final ForgeFlowingFluid.Properties SOAP_WATER_FLUID_PROPERTIES = new ForgeFlowingFluid.Properties(
            ModFluidTypes.SOAP_WATER_FLUID_TYPE, SOURCE_HYDROGRN_PEROXIDE, FLOWING_HYDROGRN_PEROXIDE)
            .slopeFindDistance(2).levelDecreasePerBlock(2).block(SpaceSimulationModBlocks.HYDROGRN_PEROXIDE_BLOCK)
            .bucket(SpaceSimulationModItems.SOURCE_HYDROGRN_PEROXIDE_BUCKET);

    // 等会在modid那个类中写的。注册在总线上
    public static void register(IEventBus eventBus) {
        FLUIDS.register(eventBus);
    }
}