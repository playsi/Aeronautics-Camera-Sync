package com.playsi.aero_cam_sync.client;

import net.minecraft.network.chat.TextColor;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.w3c.dom.Text;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MOD_ENABLED = BUILDER
            .comment("Enable Mod")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue MIN_NORMAL_Y = BUILDER
            .comment("Maximum tilt (0.0 - 0.7)")
            .defineInRange("minNormalY", 0.7, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SMOOTH_SPEED = BUILDER
            .comment("Tilt interpolation speed per frame (9999 = never moves, 0.0 = instant snap)")
            .defineInRange("smoothSpeed", 1.7, 0.0, 9999.0);

    public static final ModConfigSpec.DoubleValue SUBLEVEL_MEMORY_SECONDS = BUILDER
            .comment("How long (in seconds) to remember the last SubLevel when the player is airborne. Set to 0 to disable.")
            .defineInRange("subLevelMemorySeconds", 0.5, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue RAYCAST_DOWN_LENGTH = BUILDER
            .comment("Distance from player to floor")
            .defineInRange("fromPlayerToFloor", 7.0, 0.1, 12.0);

    public static final ModConfigSpec.BooleanValue DEBUG_RAYS = BUILDER
            .comment("Render debug raycasts (disable in production!)")
            .define("debugRays", false);

    public static final ModConfigSpec.BooleanValue ALLOW_3RD_PERSON = BUILDER
            .comment("ALLOW IN 3RD FACE §4{BETA}")
            .define("allow3rdPerson", true);

    public static final ModConfigSpec.IntValue RAYCAST_COUNT = BUILDER
            .comment("Counts of raycasts")
            .defineInRange("10 is enough", 10, 1, 10000);

    public static final ModConfigSpec.BooleanValue MODIFY_CAMERA_ROT = BUILDER
            .comment("ROTATE CAMERA")
            .define("rotate_camera", true);


    public static final ModConfigSpec.BooleanValue MODIFY_CAMERA_POS = BUILDER
            .comment("MOVE CAMERA")
            .define("move_camera", true);

    public static final ModConfigSpec.DoubleValue RAYCAST_UP_LENGTH = BUILDER
            .comment("Raycast start")
            .defineInRange("fromFoot", 0.2, -1.0, 1.0);

    public static final ModConfigSpec.BooleanValue IGNORE_SERVER = BUILDER
            .comment("Work only on client side")
            .define("ignoreServer", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}