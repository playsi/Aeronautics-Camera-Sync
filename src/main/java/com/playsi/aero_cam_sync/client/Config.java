package com.playsi.aero_cam_sync.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── General ────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue MOD_ENABLED =
            BUILDER.comment("Enable mod").define("enabled", true);

    public static final ModConfigSpec.BooleanValue ALLOW_3RD_PERSON =
            BUILDER.comment("Allow in 3rd person view (BETA)").define("allow3rdPerson", false);

    public static final ModConfigSpec.BooleanValue IGNORE_SERVER =
            BUILDER.comment("Force use client").define("clientOnly", false);

    // ── Camera ─────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue MODIFY_CAMERA_ROT =
            BUILDER.comment("Rotate camera").define("rotateCamera", true);

    public static final ModConfigSpec.BooleanValue MODIFY_CAMERA_POS =
            BUILDER.comment("Move camera").define("moveCamera", true);

    public static final ModConfigSpec.DoubleValue SMOOTH_SPEED =
            BUILDER.comment("Tilt interpolation speed per frame (0.0 = instant snap, 9999 = never moves)")
                    .defineInRange("smoothSpeed", 1.7, 0.0, 9999.0);

    public static final ModConfigSpec.DoubleValue MIN_NORMAL_Y =
            BUILDER.comment("Maximum tilt threshold (0.0 - 1.0)")
                    .defineInRange("minNormalY", 0.8, 0.0, 1.0);

    // ── Raycast ────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue RAYCAST_COUNT =
            BUILDER.comment("Number of raycasts (10 is usually enough)")
                    .defineInRange("count", 10, 1, 10000);

    public static final ModConfigSpec.DoubleValue RAYCAST_DOWN_LENGTH =
            BUILDER.comment("Distance from player down to floor")
                    .defineInRange("downLength", 7.0, 0.1, 12.0);

    public static final ModConfigSpec.DoubleValue RAYCAST_UP_LENGTH =
            BUILDER.comment("Raycast start offset from foot")
                    .defineInRange("upLength", 0.2, -1.0, 1.0);

    // ── Debug ──────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue DEBUG_MESSAGES =
            BUILDER.comment("Show debug message in console").define("debugMessages", false);

    public static final ModConfigSpec.BooleanValue DEBUG_RAYS =
            BUILDER.comment("Render debug raycasts").define("rays", false);

    public static final ModConfigSpec.BooleanValue DEBUG_PICK_RAYS =
            BUILDER.comment("Show tilted pick ray")
                    .define("pickRays", false);

    public static final ModConfigSpec SPEC = BUILDER.build();
}