package org.XYccWA.space_simulation.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SpaceSimulationConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue useUnifiedSpawn;

    static {
        BUILDER.push("Spawn Settings");

        useUnifiedSpawn = BUILDER.comment("是否使用统一出生点（所有玩家出生在同一位置）")
                .define("useUnifiedSpawn", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
