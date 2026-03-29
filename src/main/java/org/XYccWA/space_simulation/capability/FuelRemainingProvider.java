package org.XYccWA.space_simulation.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.XYccWA.space_simulation.capability.impl.FuelRemainingCapabilityImpl;

public class FuelRemainingProvider implements ICapabilitySerializable<CompoundTag> {
    private final FuelRemainingCapability capability;
    private final LazyOptional<FuelRemainingCapability> lazyOptional;

    public FuelRemainingProvider(Player player) {
        this.capability = new FuelRemainingCapabilityImpl(player);
        this.lazyOptional = LazyOptional.of(() -> capability);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == CapabilityHandler.FUEL_REMAINING ? lazyOptional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("fuel_remaining", capability.getFuelRemaining());
        tag.putFloat("max_fuel", capability.getMaxFuel());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        capability.setFuelRemaining(nbt.getFloat("fuel_remaining"));
        capability.setMaxFuel(nbt.getFloat("max_fuel"));
    }
}
