package org.XYccWA.space_simulation.capability.impl;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.XYccWA.space_simulation.SpaceSimulationMod;
import org.XYccWA.space_simulation.capability.FuelRemainingCapability;
import org.XYccWA.space_simulation.network.FuelDataSyncPacket;

public class FuelRemainingCapabilityImpl implements FuelRemainingCapability {
    private final Player player;
    private float fuelRemaining = 500.0f;
    private float maxFuel = 1000.0f;

    public FuelRemainingCapabilityImpl(Player player) {
        this.player = player;
    }

    @Override
    public float getFuelRemaining() {
        return fuelRemaining;
    }

    @Override
    public void setFuelRemaining(float fuel) {
        this.fuelRemaining = Math.min(fuel, maxFuel);
        syncData();
    }

    @Override
    public void consumeFuel(float amount) {
        this.fuelRemaining = Math.max(0, this.fuelRemaining - amount);
        syncData();
    }

    @Override
    public void addFuel(float amount) {
        this.fuelRemaining = Math.min(maxFuel, this.fuelRemaining + amount);
        syncData();
    }

    @Override
    public float getMaxFuel() {
        return maxFuel;
    }

    @Override
    public void setMaxFuel(float maxFuel) {
        this.maxFuel = maxFuel;
        syncData();
    }

    private void syncData() {
        // 只在服务器端发送数据包
        if (!this.player.level().isClientSide) {
            SpaceSimulationMod.NETWORK.send(PacketDistributor.ALL.noArg(), new FuelDataSyncPacket(fuelRemaining, maxFuel));
        }
    }
}
