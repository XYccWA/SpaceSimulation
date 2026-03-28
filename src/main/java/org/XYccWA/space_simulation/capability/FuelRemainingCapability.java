// src/main/java/org/XYccWA/space_simulation/capability/FuelRemainingCapability.java
package org.XYccWA.space_simulation.capability;

public interface FuelRemainingCapability {
    float getFuelRemaining();
    void setFuelRemaining(float fuel);
    void consumeFuel(float amount);
    void addFuel(float amount);

    // 新增方法
    float getMaxFuel();
    void setMaxFuel(float maxFuel);
}
