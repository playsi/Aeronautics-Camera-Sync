package com.playsi.aero_cam_sync.client.devtest;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.playsi.aero_cam_sync.api.AcsClientState;
import com.playsi.aero_cam_sync.api.AcsConditions;
import com.playsi.aero_cam_sync.api.AcsHandle;
import com.playsi.aero_cam_sync.api.AcsState;
import com.playsi.aero_cam_sync.api.AeroCamSyncApi;
import com.playsi.aero_cam_sync.api.ConditionContext;
import com.playsi.aero_cam_sync.api.FrameConditions;
import com.playsi.aero_cam_sync.api.FrameReport;
import com.playsi.aero_cam_sync.api.TiltContext;
import com.playsi.aero_cam_sync.api.TiltSource;
import com.playsi.aero_cam_sync.client.debug.CameraDriverProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * A test consumer of {@code TiltSource}: the {@code /settilt} command. Dev runs only, gated
 * by {@code !FMLLoader.isProduction()} in {@code AeroCamSyncClient}.
 *
 * <p>What to type and what must happen is the release checklist, not this file. A command rather
 * than a key because the tilt must be set as a number, the priority needs two sources at once, and
 * the result is checked against a snapshot.
 */
public final class TiltSourceTest {

    private TiltSourceTest() {}

    private static final String MOD_ID = "acs_test";

    private static AcsHandle acs;

    private static final float DEG = (float) (Math.PI / 180.0);

    private enum Mode {
        OFF,
        /** Its own tilt, ignoring the ACS one: "my dimension has its own gravity". */
        FIXED,
        /** The ACS tilt, scaled down: "let it roll, but half as much". */
        SCALE
    }

    private enum OffsetMode {
        NONE,
        WORLD,
        VIEW,
        NECK
    }

    /**
     * There are two of these, or the priority cannot be tested: it needs a frame BOTH claim. Fields
     * are {@code volatile}: written by the command thread, read by the render thread.
     */
    private static final class Probe implements TiltSource {

        private final String label;
        private volatile Mode mode = Mode.OFF;
        private volatile float degX = 0f;
        private volatile float degZ = 0f;
        private volatile float factor = 1f;

        private volatile OffsetMode offsetMode = OffsetMode.NONE;
        private volatile Vec3 offsetArg = Vec3.ZERO;
        private volatile float neckDrop = 0.3f;

        /**
         * Per source, not per frame: a shared opt-out would lift the ceiling off a neighbour that
         * overshot by mistake in the same frame.
         */
        private volatile boolean clampOff = false;

        /**
         * {@code eyeOffset()} needs what {@code tilt()} answered in the same frame; recomputing it
         * would let any impurity split the two.
         */
        private volatile Quaternionf frameTilt = new Quaternionf();

        private volatile Vec3 predictedCamera = Vec3.ZERO;
        /** BEFORE the wall clamp. */
        private volatile Vec3 returnedOffset = Vec3.ZERO;

        private Probe(String label) {
            this.label = label;
        }

        private boolean wantsOffset() {
            return offsetMode != OffsetMode.NONE;
        }

        @Override
        public boolean appliesTo(TiltContext context) {
            // An offset alone is reason enough to claim the frame.
            return mode != Mode.OFF || wantsOffset();
        }

        @Override
        public Quaternionf tilt(TiltContext context) {
            Quaternionf result = switch (mode) {
                // WORLD axes, so the result does not depend on where the player looks.
                case FIXED -> new Quaternionf().rotateZ(degZ * DEG).rotateX(degX * DEG);
                case SCALE -> new Quaternionf().slerp(context.acsTilt(), factor);
                // null here would mean "changed my mind", taking the offset to the next source.
                case OFF -> wantsOffset() ? context.acsTilt() : null;
            };

            frameTilt = result == null ? new Quaternionf() : new Quaternionf(result);
            return result;
        }

        @Override
        public Vec3 eyeOffset(TiltContext context) {
            Quaternionf tilt = frameTilt;

            Vec3 offset = switch (offsetMode) {
                case NONE -> Vec3.ZERO;
                case WORLD -> offsetArg;
                case VIEW -> inViewAxes(context, offsetArg);
                case NECK -> neckPivotResidual(context, tilt, neckDrop);
            };

            // From the same context, not from the fields above after the fact.
            predictedCamera = context.cameraPosFor(tilt).add(offset);
            returnedOffset = offset;
            return offset;
        }

        @Override
        public boolean eyeOffsetIsDeliberate(TiltContext context) {
            return clampOff;
        }

        private String describe() {
            String tilt = switch (mode) {
                case OFF -> label + "=off";
                case FIXED -> String.format("%s=fixed(x=%.1f z=%.1f)", label, degX, degZ);
                case SCALE -> String.format("%s=scale(%.2f)", label, factor);
            };
            String clamp = clampOff ? "!" : "";
            return clamp + switch (offsetMode) {
                case NONE -> tilt;
                case NECK -> tilt + String.format("+offset(neck %.2f)", neckDrop);
                default -> tilt + "+offset(" + offsetMode.name().toLowerCase() + " "
                        + vec(offsetArg) + ")";
            };
        }
    }

    /**
     * The API takes a WORLD vector, so a mod thinking in head axes converts it itself.
     * This is that conversion.
     */
    private static Vec3 inViewAxes(TiltContext context, Vec3 rightUpForward) {
        Vec3 look = context.player().getViewVector(context.partialTick());

        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 1.0e-6) return Vec3.ZERO; // looking straight up or down
        forward = forward.normalize();

        // At yaw 0 the look vector points to +Z and the player's right hand to -X.
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);

        return right.scale(rightUpForward.x)
                .add(0.0, rightUpForward.y, 0.0)
                .add(forward.scale(rightUpForward.z));
    }

    /**
     * The body rotates about the NECK rather than the feet, the diverging second model the offset
     * was added for. Computed from {@link TiltContext#vanillaCameraPos()} and NOT from the eye: in
     * third person the camera sits behind the player, and the offset must correct the pose rather
     * than replace it.
     */
    private static Vec3 neckPivotResidual(TiltContext context, Quaternionf tilt, float drop) {
        Vec3 camera = context.vanillaCameraPos();
        Vec3 feet = context.player().getPosition(context.partialTick());
        double eyeY = context.player().getEyePosition(context.partialTick()).y;

        Vec3 pivot = new Vec3(feet.x, eyeY - drop, feet.z);

        org.joml.Vector3f rel = new org.joml.Vector3f(
                (float) (camera.x - pivot.x),
                (float) (camera.y - pivot.y),
                (float) (camera.z - pivot.z));
        new Quaternionf(tilt).transform(rel);

        Vec3 desired = pivot.add(rel.x, rel.y, rel.z);
        return desired.subtract(context.cameraPosFor(tilt));
    }

    /**
     * Registered as its own object rather than on {@link Probe}, which is itself a test: conditions
     * must work for a mod that drives NOTHING.
     */
    private static final class TestConditions implements AcsConditions {

        private volatile boolean skipBaseline = false;

        private volatile @Nullable String skipMod = null;

        /**
         * A string, not the object: a {@code FrameReport} is valid only for the duration of the
         * call, and a retained reference silently reads another frame next time.
         */
        private volatile String lastFrame = "(no frames yet)";

        @Override
        public void conditionsFor(ConditionContext context, FrameConditions conditions) {
            if (skipBaseline) {
                conditions.skipBaseline("dev command /settilt ours off");
            }
            String target = skipMod;
            if (target != null) {
                conditions.skip(target, "dev command /settilt skip");
            }
        }

        @Override
        public void frameResolved(FrameReport report) {
            lastFrame = "winner=" + report.tiltSource()
                    + " baseline=" + report.baselineActive()
                    + " scale=" + String.format("%.3f", report.tiltScale())
                    + " offset=" + vec(report.eyeOffset())
                    + " skipped=" + report.skipped();
        }
    }

    private static final TestConditions CONDITIONS = new TestConditions();

    private static final Probe LOW = new Probe("low");
    private static final Probe HIGH = new Probe("high");

    public static void init() {
        acs = AeroCamSyncApi.forMod(MOD_ID);

        // Once at startup, as a real mod would. Zero means "I do not mind who outranks me".
        acs.addTiltSource(0, LOW);
        acs.addTiltSource(100, HIGH);

        acs.addConditions(CONDITIONS);

        NeoForge.EVENT_BUS.register(TiltSourceTest.class);
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        // Client-side, so it works on a server without the mod.
        event.getDispatcher().register(
                Commands.literal("settilt")
                        .then(named("high", HIGH))
                        .then(Commands.literal("scale")
                                .then(Commands.argument("factor", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(context -> {
                                            LOW.factor = FloatArgumentType.getFloat(context, "factor");
                                            LOW.mode = Mode.SCALE;
                                            return report(context.getSource());
                                        })))
                        .then(offset())
                        .then(Commands.literal("check")
                                .executes(context -> check(context.getSource())))
                        .then(Commands.literal("off").executes(context -> {
                            LOW.mode = Mode.OFF;
                            HIGH.mode = Mode.OFF;
                            LOW.offsetMode = OffsetMode.NONE;
                            HIGH.offsetMode = OffsetMode.NONE;
                            return report(context.getSource());
                        }))
                        .then(Commands.literal("status")
                                .executes(context -> report(context.getSource())))
                        .then(Commands.literal("clamp")
                                .then(Commands.literal("off").executes(context -> {
                                    LOW.clampOff = true;
                                    HIGH.clampOff = true;
                                    return report(context.getSource());
                                }))
                                .then(Commands.literal("on").executes(context -> {
                                    LOW.clampOff = false;
                                    HIGH.clampOff = false;
                                    return report(context.getSource());
                                })))
                        .then(Commands.literal("ours")
                                .then(Commands.literal("off").executes(context -> {
                                    CONDITIONS.skipBaseline = true;
                                    return report(context.getSource());
                                }))
                                .then(Commands.literal("on").executes(context -> {
                                    CONDITIONS.skipBaseline = false;
                                    return report(context.getSource());
                                })))
                        .then(Commands.literal("skip")
                                .then(Commands.literal("off").executes(context -> {
                                    CONDITIONS.skipMod = null;
                                    return report(context.getSource());
                                }))
                                .then(Commands.argument("modId", StringArgumentType.word())
                                        .executes(context -> {
                                            CONDITIONS.skipMod =
                                                    StringArgumentType.getString(context, "modId");
                                            return report(context.getSource());
                                        })))
                        // No literal means the low source.
                        .then(degrees(LOW)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> named(String name, Probe probe) {
        return Commands.literal(name).then(degrees(probe));
    }

    /** Degrees about the world X and Z axes. */
    private static RequiredArgumentBuilder<CommandSourceStack, Float> degrees(Probe probe) {
        return Commands.argument("degX", FloatArgumentType.floatArg(-90f, 90f))
                .then(Commands.argument("degZ", FloatArgumentType.floatArg(-90f, 90f))
                        .executes(context -> {
                            probe.degX = FloatArgumentType.getFloat(context, "degX");
                            probe.degZ = FloatArgumentType.getFloat(context, "degZ");
                            probe.mode = Mode.FIXED;
                            return report(context.getSource());
                        }));
    }

    /**
     * Always on the LOW source. The 8-block argument limit sits above the ceiling on purpose, so
     * the warning can be triggered by command.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> offset() {
        return Commands.literal("offset")
                .then(Commands.literal("off").executes(context -> {
                    LOW.offsetMode = OffsetMode.NONE;
                    return report(context.getSource());
                }))
                .then(Commands.literal("neck")
                        .then(Commands.argument("drop", FloatArgumentType.floatArg(0f, 2f))
                                .executes(context -> {
                                    LOW.neckDrop = FloatArgumentType.getFloat(context, "drop");
                                    LOW.offsetMode = OffsetMode.NECK;
                                    return report(context.getSource());
                                })))
                .then(vector("view", OffsetMode.VIEW))
                .then(vector(null, OffsetMode.WORLD));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> vector(
            @Nullable String literal, OffsetMode target) {

        RequiredArgumentBuilder<CommandSourceStack, Float> node =
                Commands.argument("x", FloatArgumentType.floatArg(-8f, 8f))
                        .then(Commands.argument("y", FloatArgumentType.floatArg(-8f, 8f))
                                .then(Commands.argument("z", FloatArgumentType.floatArg(-8f, 8f))
                                        .executes(context -> {
                                            LOW.offsetArg = new Vec3(
                                                    FloatArgumentType.getFloat(context, "x"),
                                                    FloatArgumentType.getFloat(context, "y"),
                                                    FloatArgumentType.getFloat(context, "z"));
                                            LOW.offsetMode = target;
                                            return report(context.getSource());
                                        })));

        return literal == null ? node : Commands.literal(literal).then(node);
    }

    /**
     * Read from a SNAPSHOT rather than the fields above, or the test tests itself.
     */
    private static int report(CommandSourceStack source) {
        say(source, LOW.describe() + " " + HIGH.describe());

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 1;

        AcsState state = acs.state(mc.player, 1.0f);
        AcsClientState client = state.client();

        say(source, "tiltSource=" + (client == null ? "?" : String.valueOf(client.tiltSource()))
                + " applied=" + state.tiltApplied()
                + " suppressed=" + state.suppressed() + state.suppressedBy()
                + " oursOff=" + CONDITIONS.skipBaseline
                + " skip=" + CONDITIONS.skipMod
                + " clampOff=" + LOW.clampOff
                // Non-zero for a seated player is Sable: why the ACS predicate excludes vehicles.
                + " foreignCameraRot="
                + String.format("%.2f", CameraDriverProbe.lastDegrees()) + "°"
                + (client == null ? "" : " scale=" + String.format("%.3f", client.tiltScale()))
                + " angle=" + String.format("%.1f", angleDegrees(client))
                + " eyeOffset=" + vec(state.eyeOffset()));

        // The lines must agree, or snapshot and report are looking at different frames.
        say(source, "frame " + CONDITIONS.lastFrame);
        return 1;
    }

    /**
     * The main test of the offset: that the camera position has ONE model, not two. Two legitimate
     * causes of a non-zero delta are printed alongside: the collision clamp and {@code tiltScale}
     * attenuation. Anything else is a formula divergence inside ACS.
     */
    private static int check(CommandSourceStack source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        AcsState state = acs.state(mc.player, 1.0f);
        AcsClientState client = state.client();
        if (client == null) {
            say(source, "client() = null - not a client, nothing to check");
            return 0;
        }

        if (client.tiltSource() == null) {
            say(source, "nobody claimed the frame - run /settilt offset first");
            return 0;
        }

        Vec3 predicted = LOW.predictedCamera;
        Vec3 actual = client.cameraPos();
        double delta = predicted.distanceTo(actual);

        say(source, "camera predicted " + vec(predicted) + " actual " + vec(actual)
                + " delta=" + String.format("%.4f", delta));

        say(source, "offset returned " + vec(LOW.returnedOffset)
                + " applied " + vec(state.eyeOffset())
                + " scale=" + String.format("%.3f", client.tiltScale()));

        // Zero here while the camera moved means the rays did not get the correction.
        say(source, "aimEye − vanillaEye = "
                + vec(state.aimEye().subtract(state.vanillaEye()))
                + " (of which offset " + vec(state.eyeOffset()) + ")");

        say(source, delta < 1.0e-3
                ? "OK: the formulas agree"
                : "DIVERGENCE. Expected causes: movement (the prediction is from the previous"
                        + " frame, so check while standing still), the wall clamp, scale="
                        + String.format("%.3f", client.tiltScale())
                        + " (attenuation), and the offset ceiling "
                        + (LOW.clampOff
                                ? "off" : "on - a vector over four blocks is clamped")
                        + ". If none of them fit, the formulas have drifted apart.");
        return 1;
    }

    private static float angleDegrees(@Nullable AcsClientState client) {
        if (client == null) return 0f;

        Quaternionf delta = new Quaternionf(client.vanillaCameraRot()).conjugate()
                .mul(client.cameraRot()).normalize();
        return (float) Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(delta.w()))));
    }

    private static String vec(Vec3 v) {
        return String.format("(%.3f %.3f %.3f)", v.x, v.y, v.z);
    }

    private static void say(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("[acs_test/source] " + message), false);
    }
}
