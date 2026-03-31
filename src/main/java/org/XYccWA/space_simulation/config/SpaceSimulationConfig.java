package org.XYccWA.space_simulation.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SpaceSimulationConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue useUnifiedSpawn;
    public static final ForgeConfigSpec.DoubleValue accelerationThreshold;

    static {
        BUILDER.push("Spawn Settings");

        useUnifiedSpawn = BUILDER.comment("是否使用统一出生点（所有玩家出生在同一位置）")
                .define("useUnifiedSpawn", true);

        BUILDER.push("High G Force Damage Settings");
        accelerationThreshold = BUILDER.comment("玩家受到高g力伤害的加速度阈值")
                .defineInRange("accelerationThreshold", 300.0, 0, 10000);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
