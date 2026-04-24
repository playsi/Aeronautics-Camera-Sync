package com.playsi.aero_cam_sync;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MOD_ENABLED = BUILDER
            .comment("Enable Mod")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue MIN_NORMAL_Y = BUILDER
            .comment("Maximum tilt (0.0 - 0.7)")
            .defineInRange("minNormalY", 0.5, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}