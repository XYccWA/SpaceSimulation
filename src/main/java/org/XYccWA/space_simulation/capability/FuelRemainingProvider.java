// src/main/java/org/XYccWA/space_simulation/capability/FuelRemainingProvider.java
package org.XYccWA.space_simulation.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.XYccWA.space_simulation.capability.impl.FuelRemainingCapabilityImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FuelRemainingProvider implements ICapabilitySerializable<CompoundTag> {
    private final FuelRemainingCapabilityImpl capability = new FuelRemainingCapabilityImpl();
    private final LazyOptional<FuelRemainingCapability> optional = LazyOptional.of(() -> capability);

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return CapabilityHandler.FUEL_REMAINING.orEmpty(cap, optional);
    }

    // src/main/java/org/XYccWA/space_simulation/capability/FuelRemainingProvider.java
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("fuel_remaining", capability.getFuelRemaining());
        tag.putFloat("max_fuel", capability.getMaxFuel()); // 添加最大燃料值
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        capability.setFuelRemaining(nbt.getFloat("fuel_remaining"));
        capability.setMaxFuel(nbt.getFloat("max_fuel")); // 加载最大燃料值
    }

}
