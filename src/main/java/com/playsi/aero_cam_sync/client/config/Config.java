package com.playsi.aero_cam_sync.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Config {
    private static final List<String> DEFAULT_CLIENT_BLACKLIST_IDS = List.of("minecraft:bow", "minecraft:crossbow", "minecraft:fishing_rod", "minecraft:snowball", "minecraft:egg", "minecraft:wind_charge", "minecraft:ender_pearl", "minecraft:brush", "minecraft:trident", "minecraft:bucket", "minecraft:water_bucket", "minecraft:lava_bucket", "minecraft:cod_bucket", "minecraft:tropical_fish_bucket", "minecraft:pufferfish_bucket", "minecraft:salmon_bucket", "minecraft:axolotl_bucket", "minecraft:tadpole_bucket", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:experience_bottle", "create:potato_cannon", "create:honey_bucket", "create:chocolate_bucket", "create:handheld_worldshaper", "create:schematic_and_quill", "create:schematic", "simulated:plunger_launcher", "aeronautics:levitite_blend_bucket");
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── General ────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue MOD_ENABLED =
            BUILDER.comment("Enable mod").define("enabled", true);

    public static final ModConfigSpec.BooleanValue ALLOW_3RD_PERSON =
            BUILDER.comment("Allow in 3rd person view (BETA)").define("allow3rdPerson", false);

    public static final ModConfigSpec.BooleanValue IGNORE_SERVER =
            BUILDER.comment("Force use client").define("clientOnly", false);

    public static final ModConfigSpec.ConfigValue<String> TOGGLE_KEY =
            BUILDER.comment("Toggle mod keybind")
                    .define("toggleKey", "key.keyboard.i");

    public static final ModConfigSpec.ConfigValue<String> OPEN_CONFIG_KEY =
            BUILDER.comment("Open config screen keybind")
                    .define("openConfigKey", "");

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

    // ── Client Blacklist ───────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue CLIENT_BLACKLIST_ENABLED =
            BUILDER.comment("Disable camera tilt when player holds a listed item (client-side)")
                    .define("clientBlacklistEnabled", true);

    public static final ModConfigSpec.BooleanValue CONSIDER_OFFHAND =
            BUILDER.comment("Consider item in offhand for blacklist checking")
                    .define("considerOffhand", true);

    public static final ModConfigSpec.ConfigValue<String> ADD_MAINHAND_ITEM_KEY =
            BUILDER.comment("Add mainhand item to blacklist if not already present")
                    .define("addMainhandItemKey", "");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CLIENT_BLACKLIST_IDS =
            BUILDER.comment("Item IDs that disable camera tilt (client-side), e.g. minecraft:bow")
                    .defineListAllowEmpty("clientBlacklistIds",
                            DEFAULT_CLIENT_BLACKLIST_IDS,
                            o -> o instanceof String);

    // ── Server Blacklist ───────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue SERVER_BLACKLIST_ENABLED =
            BUILDER.comment("Disable camera tilt when player holds a listed item (server-side)")
                    .define("serverBlacklistEnabled", false);


    public static final ModConfigSpec.ConfigValue<List<? extends String>> SERVER_BLACKLIST_IDS =
            BUILDER.comment("Item IDs that disable camera tilt (server-side), e.g. minecraft:bow")
                    .defineListAllowEmpty("serverBlacklistIds",
                            java.util.Collections.emptyList(),
                            o -> o instanceof String);

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

    public static final ModConfigSpec.BooleanValue DROP_CACHE_ON_ALL_MISS =
            BUILDER.comment("Immediately forgot sublevel when player step off")
                    .define("dropOnAllMiss", true);

    public static final ModConfigSpec.BooleanValue DISABLE_ON_FLYING =
            BUILDER.comment("Stop camera tilt when flying (Creative or Spectator)")
                    .define("disableOnFlying", true);



    // ── Debug ──────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue DEBUG_RAYS =
            BUILDER.comment("Render debug raycasts").define("rays", false);

    public static final ModConfigSpec.BooleanValue DEBUG_PICK_RAYS =
            BUILDER.comment("Show tilted pick ray")
                    .define("pickRays", false);

    public static final ModConfigSpec.DoubleValue PICK_RAYS_TIME_SEC =
            BUILDER.comment("time until the pick rays disappear")
                    .defineInRange("pickRaysTime", 3.0, 0.01, 600.0);


    public static final ModConfigSpec.BooleanValue DEBUG_MESSAGES =
            BUILDER.comment("Show debug message in console").define("debugMessages", false);


    // ── Meta ──────────────────────────────────────────────────────────────────
    public static final int CURRENT_CONFIG_SCHEMA = 3; // breaking changes

    public static final ModConfigSpec.IntValue CONFIG_SCHEMA_VERSION =
            BUILDER.comment(
                    "Config schema version. Do not edit manually.\n" +
                            "Used to detect when a config reset prompt should be shown."
            ).defineInRange("configSchemaVersion", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean isLoaded() {
        try {
            MOD_ENABLED.get();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}