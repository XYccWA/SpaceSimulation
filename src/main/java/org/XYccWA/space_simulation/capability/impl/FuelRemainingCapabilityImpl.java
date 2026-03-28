// src/main/java/org/XYccWA/space_simulation/capability/impl/FuelRemainingCapabilityImpl.java
package org.XYccWA.space_simulation.capability.impl;

import org.XYccWA.space_simulation.capability.FuelRemainingCapability;

public class FuelRemainingCapabilityImpl implements FuelRemainingCapability {
    private float fuelRemaining = 500.0f; // 默认燃料值
    private float maxFuel = 10000.0f; // 默认最大燃料值

    @Override
    public float getFuelRemaining() {
        return fuelRemaining;
    }

    @Override
    public void setFuelRemaining(float fuel) {
        this.fuelRemaining = Math.min(fuel, maxFuel);
    }

    @Override
    public void consumeFuel(float amount) {
        this.fuelRemaining = Math.max(0, this.fuelRemaining - amount);
    }

    @Override
    public void addFuel(float amount) {
        this.fuelRemaining = Math.min(maxFuel, this.fuelRemaining + amount);
    }

    @Override
    public float getMaxFuel() {
        return maxFuel;
    }

    @Override
    public void setMaxFuel(float maxFuel) {
        this.maxFuel = maxFuel;
        // 如果当前燃料值超过新的最大值，则将其限制为最大值
        if (this.fuelRemaining > maxFuel) {
            this.fuelRemaining = maxFuel;
        }
    }
}
