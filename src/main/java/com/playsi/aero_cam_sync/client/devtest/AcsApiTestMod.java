package com.playsi.aero_cam_sync.client.devtest;

import com.mojang.blaze3d.platform.InputConstants;
import com.playsi.aero_cam_sync.api.AcsClientState;
import com.playsi.aero_cam_sync.api.AcsConditions;
import com.playsi.aero_cam_sync.api.AcsHandle;
import com.playsi.aero_cam_sync.api.ConditionContext;
import com.playsi.aero_cam_sync.api.FrameConditions;
import com.playsi.aero_cam_sync.api.FrameReport;
import com.playsi.aero_cam_sync.api.AcsRay;
import com.playsi.aero_cam_sync.api.AcsState;
import com.playsi.aero_cam_sync.api.AeroCamSyncApi;
import com.playsi.aero_cam_sync.api.AimPolicy;
import com.playsi.aero_cam_sync.api.TiltListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * A test consumer of the public API. Dev runs only, behind {@code DEV_API_TEST} in
 * {@code AeroCamSyncClient}, not in a method here, because a {@code KeyMapping} constructor
 * registers itself in static Minecraft registries and merely loading the class would leave four
 * foreign keybinds in the built mod.
 *
 * <p>This code goes into the documentation verbatim, so keep it written the way a
 * third-party mod would write it: nothing here may touch an internal class, only
 * {@code com.playsi.aero_cam_sync.api}.
 *
 * <p>It cannot test the soft-dependency path ({@code ModList.isLoaded}, behaviour without ACS on
 * the classpath): this code is inside ACS. That is a checklist item.
 */
public final class AcsApiTestMod {

    private AcsApiTestMod() {}

    /** One handle per mod, fine to keep in a static. */
    private static final String MOD_ID = "acs_test";

    private static AcsHandle acs;

    /** What to answer for this mod's own probe rays. */
    private enum PolicyMode {
        /** How a real mod should behave almost always. */
        PASS,
        /** "This is my aim ray, even though it does not start at the eye." */
        SHIFT,
        /** "This is suspension, hands off": the physics case from Issue #30. */
        KEEP_VANILLA,
        /** Two policies answer opposite: one log line, and the first wins. */
        CONFLICT
    }

    private static PolicyMode mode = PolicyMode.PASS;

    /**
     * Identified by REFERENCE to the {@code ClipContext} this class created. Answering for every ray
     * instead is useless: {@code SHIFT} then moves foreign physics and {@code KEEP_VANILLA} kills
     * all aiming, so "the policy works" cannot be told from "the policy broke the game". This is
     * also the documented advice: answer {@code PASS} to anything that is not your own ray.
     */
    private static ClipContext probeContext = null;

    /** Outside the net's 1e-4 tolerance, so its standard rule rejects probe "A". */
    private static final double PROBE_MARK = 0.01;

    private static final KeyMapping SUPPRESS = devKey("suppress", GLFW.GLFW_KEY_LEFT_BRACKET);
    private static final KeyMapping SNAPSHOT = devKey("snapshot", GLFW.GLFW_KEY_RIGHT_BRACKET);
    private static final KeyMapping VANILLA_EYE = devKey("vanilla_eye", GLFW.GLFW_KEY_BACKSLASH);
    private static final KeyMapping POLICY = devKey("policy", GLFW.GLFW_KEY_BACKSPACE);
    private static final KeyMapping PROBE = devKey("probe", GLFW.GLFW_KEY_APOSTROPHE);
    private static final KeyMapping COLLISION = devKey("collision", GLFW.GLFW_KEY_SEMICOLON);
    private static final KeyMapping THIRD_PERSON = devKey("third_person", GLFW.GLFW_KEY_PERIOD);

    private static KeyMapping devKey(String name, int code) {
        return new KeyMapping("key.aero_cam_sync.devtest." + name,
                InputConstants.Type.KEYSYM, code, "key.category.aero_cam_sync");
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SUPPRESS);
        event.register(SNAPSHOT);
        event.register(VANILLA_EYE);
        event.register(POLICY);
        event.register(PROBE);
        event.register(COLLISION);
        event.register(THIRD_PERSON);
    }

    public static void init() {
        acs = AeroCamSyncApi.forMod(MOD_ID);

        // Registered once at startup; what changes is the answers, not the registration.
        acs.addConditions(CONDITIONS);

        acs.addListener(new TiltListener() {
            @Override public void onTiltStart(AcsState state) { chat("tilt start"); }
            @Override public void onTiltStop(AcsState state) { chat("tilt stop"); }
            @Override public void onSuppressionChanged(boolean suppressed, List<String> by) {
                chat("suppression " + (suppressed ? "on by " + by : "off"));
            }
        });

        // The second policy exists only to disagree with the first in CONFLICT mode. Both reject
        // foreign rays first, which is the rule for real mods.
        acs.addPolicy(query -> {
            if (query.context() != probeContext) return AimPolicy.Decision.PASS;
            return switch (mode) {
                case SHIFT, CONFLICT -> AimPolicy.Decision.SHIFT;
                case KEEP_VANILLA -> AimPolicy.Decision.KEEP_VANILLA;
                case PASS -> AimPolicy.Decision.PASS;
            };
        });
        acs.addPolicy(query -> query.context() == probeContext && mode == PolicyMode.CONFLICT
                ? AimPolicy.Decision.KEEP_VANILLA
                : AimPolicy.Decision.PASS);
    }

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        while (SUPPRESS.consumeClick()) {
            acs.suppress(3_000);
            chat("suppress(3000), mine=" + acs.isSuppressedByMe());
        }

        while (SNAPSHOT.consumeClick()) {
            printSnapshot(player);
        }

        while (VANILLA_EYE.consumeClick()) {
            compareVanillaEyeScope(player);
        }

        while (POLICY.consumeClick()) {
            PolicyMode[] all = PolicyMode.values();
            mode = all[(mode.ordinal() + 1) % all.length];
            chat("aim policy mode -> " + mode);
        }

        while (PROBE.consumeClick()) {
            fireProbes(player);
        }

        while (COLLISION.consumeClick()) {
            toggleCameraCollision();
        }

        while (THIRD_PERSON.consumeClick()) {
            toggleThirdPerson();
        }
    }

    /**
     * The flags are restated EVERY frame, which is the test of the mechanism: stop stating one and
     * the condition disappears in the same frame, with no "clear" call. {@code volatile}: written
     * by the input thread, read by the render thread.
     */
    private static final class TestConditions implements AcsConditions {

        private volatile boolean thirdPerson = false;
        private volatile boolean collisionTakenOver = false;

        /** Unpacked: the {@code FrameReport} object must not be retained. */
        private volatile String lastFrame = "(no frames yet)";

        @Override
        public void conditionsFor(ConditionContext context, FrameConditions conditions) {
            if (thirdPerson) {
                conditions.baselineInThirdPerson("test: scenario lives in third person");
            }
            if (collisionTakenOver) {
                conditions.takeOverCameraCollision(
                        "test: pretending to rotate the player for real");
            }
        }

        @Override
        public void frameResolved(FrameReport report) {
            lastFrame = "winner=" + report.tiltSource()
                    + " baseline=" + report.baselineActive()
                    + " firstPerson=" + report.firstPerson()
                    + " scale=" + String.format("%.3f", report.tiltScale())
                    + " skipped=" + report.skipped();
        }
    }

    private static final TestConditions CONDITIONS = new TestConditions();

    /**
     * A real consumer answers this EVERY frame from its own predicate; the key only stands in for
     * that predicate. Note the price of frame conditions: "forgot to state it" and "switched it
     * off" are the same state.
     */
    private static void toggleThirdPerson() {
        boolean on = !CONDITIONS.thirdPerson;
        CONDITIONS.thirdPerson = on;
        chat("baselineInThirdPerson " + (on ? "ON" : "OFF") + " (restated every frame)");
    }

    /**
     * What a mod that genuinely rotates the player does. Frame-scoped, so "who else holds it" is
     * not a question: it is held by whoever stated it THIS frame, per the phase 2 report.
     */
    private static void toggleCameraCollision() {
        boolean on = !CONDITIONS.collisionTakenOver;
        CONDITIONS.collisionTakenOver = on;
        chat("takeOverCameraCollision " + (on ? "ON" : "OFF") + " (restated every frame)");
    }

    /**
     * Two probe rays, one per policy decision. "Shifted or not" is measured against a reference
     * computed inside {@code withVanillaEye}, where the net is off, so the policies never see it.
     *
     * <pre>
     * mode           A (not from eye)  B (exactly from eye)
     * PASS           false             true
     * SHIFT          true              true
     * KEEP_VANILLA   false             false
     * CONFLICT       true              true   + one dispute line in the log
     * </pre>
     */
    private static void fireProbes(LocalPlayer player) {
        double reach = player.blockInteractionRange();
        Vec3 dir = player.getViewVector(1.0f);
        Vec3 eye = player.getEyePosition(1.0f);

        chat("probes in mode " + mode + ":");
        probe(player, "A eye+" + PROBE_MARK + " (not from eye)", eye.add(0, PROBE_MARK, 0), dir, reach);
        probe(player, "B eye            (exactly from eye)", eye, dir, reach);
    }

    private static void probe(LocalPlayer player, String label, Vec3 from, Vec3 dir, double reach) {
        Vec3 to = from.add(dir.scale(reach));

        BlockHitResult reference = acs.withVanillaEye(() -> clipProbe(player, from, to));
        BlockHitResult actual = clipProbe(player, from, to);

        boolean shifted = reference.getLocation().distanceToSqr(actual.getLocation()) > 1.0e-8;
        chat("  " + label + " shifted=" + shifted + " " + hit(actual));
    }

    /** The context reference is what the policy identifies as its own. */
    private static BlockHitResult clipProbe(LocalPlayer player, Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        probeContext = context;
        try {
            return player.level().clip(context);
        } finally {
            probeContext = null;
        }
    }

    /**
     * Taken once, then read. Every {@code vanilla*} / {@code aim*} pair must agree except while
     * standing on a tilted sub-level in first person.
     */
    private static void printSnapshot(LocalPlayer player) {
        AcsState state = acs.state(player, 1.0f);

        chat("enabled=" + state.modEnabled()
                + " tilt=" + state.tiltApplied()
                + " suppressed=" + state.suppressed() + state.suppressedBy());

        chat("eye  " + vec(state.vanillaEye()) + " -> " + vec(state.aimEye()));
        chat("look " + vec(state.vanillaLook(1.0f)) + " -> " + vec(state.aimLook(1.0f)));

        AcsRay vanilla = state.vanillaRay(player.blockInteractionRange());
        AcsRay aim = state.aimRay(player.blockInteractionRange());
        chat("ray end " + vec(vanilla.to()) + " -> " + vec(aim.to()));

        AcsClientState client = state.client();
        if (client == null) {
            chat("client half: null (server side)");
            return;
        }
        chat("cam  " + vec(client.vanillaCameraPos()) + " -> " + vec(client.cameraPos()));
        chat("scale=" + String.format("%.3f", client.tiltScale())
                + " firstPerson=" + client.firstPerson()
                + " sub=" + (client.tiltSubLevel() != null));

        // This frame's conditions and how it ended. There used to be holder registries here; "who
        // did what" is now a per-frame question, answered by phase 2.
        chat("conditions: thirdPerson=" + CONDITIONS.thirdPerson
                + " collision=" + CONDITIONS.collisionTakenOver);
        chat("frame " + CONDITIONS.lastFrame);
    }

    /**
     * Verifies that {@code withVanillaEye} really removes the correction rather than merely
     * claiming to: the same ray inside and outside the scope must give DIFFERENT hit points while
     * the camera is tilted, and identical ones while it is not.
     */
    private static void compareVanillaEyeScope(LocalPlayer player) {
        Level level = player.level();
        Vec3 from = player.getEyePosition(1.0f);
        Vec3 to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));

        BlockHitResult outside = clip(level, player, from, to);
        BlockHitResult inside = acs.withVanillaEye(() -> clip(level, player, from, to));

        chat("clip outside scope: " + hit(outside));
        chat("clip inside  scope: " + hit(inside));
        chat("delta = " + String.format("%.4f",
                outside.getLocation().distanceTo(inside.getLocation())));
    }

    private static BlockHitResult clip(Level level, LocalPlayer player, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static String hit(BlockHitResult result) {
        return result.getType() == HitResult.Type.MISS
                ? "MISS " + vec(result.getLocation())
                : result.getBlockPos().toShortString() + " " + vec(result.getLocation());
    }

    private static String vec(Vec3 v) {
        return String.format("(%.3f %.3f %.3f)", v.x, v.y, v.z);
    }

    private static void chat(String message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.displayClientMessage(Component.literal("[acs_test] " + message), false);
    }
}
