package com.playsi.aero_cam_sync.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Config {
    // По умолчанию почти пустой: снаряды/вёдра наклоняются сервером (на сервере с
    // модом) либо авто-отключаются по классу предмета на клиент-онли сервере
    // (AUTO_DISABLE_FOR_RAYCAST_ITEMS). Здесь — ручные исключения для предметов,
    // которые не ловятся авто-детектом по классу (не Projectile/BucketItem).
    private static final List<String> DEFAULT_CLIENT_BLACKLIST_IDS = List.of(
            "create:handheld_worldshaper",
            "create:potato_cannon"
    );
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

    // Works only when the mod is installed on the server (see PlayerDropTiltMixin,
    // gated by ServerPlayer + ServerTiltStore) — no effect in client-only mode.
    public static final ModConfigSpec.BooleanValue DROP_FROM_CAMERA =
            BUILDER.comment("Drop tossed items from the tilted camera (origin + direction) instead of the hitbox head\n" +
                            "Requires the mod to be installed on the server — no effect otherwise.")
                    .define("dropFromCamera", true);

    // NB: коллизия проверяется только по блокам обычного мира. Внутри блоков
    //     самого Sable-сабвела (плота/контраптиона) она НЕ считается — просто знать.
    public static final ModConfigSpec.BooleanValue CAMERA_COLLISION =
            BUILDER.comment("Keep the shifted camera out of solid blocks (prevents seeing through walls / X-ray when tilted next to a wall)")
                    .define("cameraCollision", true);

    public static final ModConfigSpec.DoubleValue CAMERA_COLLISION_SMOOTH =
            BUILDER.comment("How smoothly the tilt eases off as the camera nears a wall, in ticks (0 = instant snap, higher = smoother)")
                    .defineInRange("cameraCollisionSmooth", 0.350, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue SMOOTH_SPEED =
            BUILDER.comment("Tilt interpolation speed per frame (0.0 = instant snap, 9999 = never moves)")
                    .defineInRange("smoothSpeed", 1.7, 0.0, 9999.0);

    public static final ModConfigSpec.DoubleValue MIN_NORMAL_Y =
            BUILDER.comment("Maximum tilt threshold (0.0 - 1.0)")
                    .defineInRange("minNormalY", 0.8, 0.0, 1.0);

    // ── Activation thresholds (per sub-level you stand on) ───────────────────────
    // Each criterion is independent: when enabled, the sub-level must be >= the value,
    // otherwise the camera does not tilt. All disabled by default = always tilt.
    public static final ModConfigSpec.BooleanValue GATE_MASS_ENABLED =
            BUILDER.comment("Only tilt when the sub-level mass is at least the threshold (primary factor)")
                    .define("gateMassEnabled", false);
    public static final ModConfigSpec.DoubleValue GATE_MASS_MIN =
            BUILDER.comment("Minimum sub-level mass to allow tilt")
                    .defineInRange("gateMassMin", 10.0, 0.0, 100_000_000.0);

    public static final ModConfigSpec.BooleanValue GATE_BLOCKS_ENABLED =
            BUILDER.comment("Only tilt when the sub-level block count is at least the threshold")
                    .define("gateBlocksEnabled", false);
    public static final ModConfigSpec.IntValue GATE_BLOCKS_MIN =
            BUILDER.comment("Minimum sub-level block count to allow tilt")
                    .defineInRange("gateBlocksMin", 10, 0, 100_000_000);

    public static final ModConfigSpec.BooleanValue GATE_LENGTH_ENABLED =
            BUILDER.comment("Only tilt when the sub-level length (X) is at least the threshold")
                    .define("gateLengthEnabled", false);
    public static final ModConfigSpec.IntValue GATE_LENGTH_MIN =
            BUILDER.comment("Minimum sub-level length in blocks (X) to allow tilt")
                    .defineInRange("gateLengthMin", 3, 1, 100_000);

    public static final ModConfigSpec.BooleanValue GATE_HEIGHT_ENABLED =
            BUILDER.comment("Only tilt when the sub-level height (Y) is at least the threshold")
                    .define("gateHeightEnabled", false);
    public static final ModConfigSpec.IntValue GATE_HEIGHT_MIN =
            BUILDER.comment("Minimum sub-level height in blocks (Y) to allow tilt")
                    .defineInRange("gateHeightMin", 3, 1, 100_000);

    public static final ModConfigSpec.BooleanValue GATE_WIDTH_ENABLED =
            BUILDER.comment("Only tilt when the sub-level width (Z) is at least the threshold")
                    .define("gateWidthEnabled", false);
    public static final ModConfigSpec.IntValue GATE_WIDTH_MIN =
            BUILDER.comment("Minimum sub-level width in blocks (Z) to allow tilt")
                    .defineInRange("gateWidthMin", 3, 1, 100_000);

    // ── Client Blacklist ───────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue CLIENT_BLACKLIST_ENABLED =
            BUILDER.comment("Disable camera tilt when player holds a listed item (client-side)")
                    .define("clientBlacklistEnabled", true);

    public static final ModConfigSpec.BooleanValue CONSIDER_OFFHAND =
            BUILDER.comment("Consider item in offhand for blacklist checking")
                    .define("considerOffhand", true);

    public static final ModConfigSpec.BooleanValue AUTO_DISABLE_FOR_RAYCAST_ITEMS =
            BUILDER.comment(
                    "Automatically disable camera tilt for projectile-like and bucket-like items,\n" +
                            "detected by item class instead of a fixed ID list. Covers modded snowballs,\n" +
                            "throwables, buckets, bows, etc. and fixes their wrong throw/placement direction\n" +
                            "while standing on a tilted sub-level.")
                    .define("autoDisableForRaycastItems", true);

    public static final ModConfigSpec.ConfigValue<String> ADD_MAINHAND_ITEM_KEY =
            BUILDER.comment("Add mainhand item to blacklist if not already present")
                    .define("addMainhandItemKey", "");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CLIENT_BLACKLIST_IDS =
            BUILDER.comment("Item IDs that disable camera tilt (client-side), e.g. minecraft:bow")
                    .defineListAllowEmpty("clientBlacklistIds",
                            DEFAULT_CLIENT_BLACKLIST_IDS,
                            o -> o instanceof String);

    // ── Server Blacklist ───────────────────────────────────────────────────────
    // TODO: серверный чёрный список отключён — с ним нужно работать отдельно
    //   (нигде не читается, требует серверной части: синхронизация списка,
    //    проверка на server-миксинах, права/команды). Раскомментировать вместе
    //    с реализацией и ServerBlacklistCategory.
//    public static final ModConfigSpec.BooleanValue SERVER_BLACKLIST_ENABLED =
//            BUILDER.comment("Disable camera tilt when player holds a listed item (server-side)")
//                    .define("serverBlacklistEnabled", false);
//
//
//    public static final ModConfigSpec.ConfigValue<List<? extends String>> SERVER_BLACKLIST_IDS =
//            BUILDER.comment("Item IDs that disable camera tilt (server-side), e.g. minecraft:bow")
//                    .defineListAllowEmpty("serverBlacklistIds",
//                            java.util.Collections.emptyList(),
//                            o -> o instanceof String);

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