package com.playsi.aero_cam_sync.client.tilt;

import com.playsi.aero_cam_sync.ClipNet;
import com.playsi.aero_cam_sync.apiimpl.Conditions;
import com.playsi.aero_cam_sync.apiimpl.SuppressionLeases;
import com.playsi.aero_cam_sync.apiimpl.TiltSources;
import com.playsi.aero_cam_sync.client.camera.FrameVanillaState;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.sublevel.SubLevelTracker;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Smoothed terrain tilt, and its application to the vanilla camera. State is static because there
 * is only one camera; writing goes through {@link Frame}, which {@link #forMainCamera(Camera)}
 * hands to the main camera only.
 */
public final class CameraController {

    private CameraController() {}

    static final Quaternionf smoothedTilt = new Quaternionf();
    private static boolean wasApplyingTilt = false;

    // 1 = no wall nearby (full tilt), 0 = camera flush against one. Smoothed.
    private static float wallScale = 1.0f;

    private static Boolean lastFirstPerson = null;

    /** {@link #updateWallScale} must reset {@code wallScale} instantly on a mode change. */
    private static boolean cameraModeChanged = false;

    /**
     * A frame snapshot, not a live read: five aim sites ask and all must get the same answer
     * within one frame, or a ray leaves with a tilted direction and an untilted origin.
     *
     * <p>The next three fields are {@code volatile}: written on the render thread, read from the
     * client tick too.
     */
    private static volatile boolean thirdPersonAllowed = false;

    /**
     * The mod that set the tilt in THIS frame, or {@code null} for the ACS baseline. Reset in
     * {@link #applyTickState(float)} because that is the one call made UNCONDITIONALLY; reset
     * anywhere later and the last winner hangs around forever.
     */
    @javax.annotation.Nullable
    private static volatile String tiltSourceMod = null;

    /** RAW eye offset from a foreign source; the wall clamp is applied in read. */
    private static volatile Vec3 sourceEyeOffset = Vec3.ZERO;

    /**
     * Write handle, obtainable ONLY for the main camera. A type rather than a guard, because
     * {@code Camera#setup} runs for secondary cameras too and one recomputing {@link #wallScale}
     * from its own position corrupts the main view.
     */
    public static final class Frame {

        private Frame() {}

        public void captureVanilla(Camera camera) {
            FrameVanillaState.capture(camera);
        }

        public void tickApplyState(float partialTick) {
            applyTickState(partialTick);
        }

        public void updateSmoothedTilt(@javax.annotation.Nullable Vector3f surfaceNormal,
                                       float deltaTime, float partialTick, boolean freeze) {
            slerpSmoothedTilt(surfaceNormal, deltaTime, partialTick, freeze);
        }

        public void applyTiltToCamera(Camera camera, float partialTick) {
            applyToCamera(camera, partialTick);
        }
    }

    private static final Frame FRAME = new Frame();

    @javax.annotation.Nullable
    public static Frame forMainCamera(Camera camera) {
        return camera == Minecraft.getInstance().gameRenderer.getMainCamera() ? FRAME : null;
    }

    /**
     * Whether the tilt MECHANISM is alive this frame. {@code false} here stops foreign sources
     * too, so conditions about the ACS scenario alone belong in {@code banned} in {@code CameraMixin},
     * not here.
     */
    public static boolean shouldApplyTilt() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;

        // First: a source that already claimed this frame keeps it even with ACS switched off.
        if (tiltSourceMod != null) return true;

        if (!Config.MOD_ENABLED.get()) return false;

        return isThirdPersonAllowed()
                || mc.options.getCameraType().isFirstPerson();
    }

    /** The ONE third-person gate. Never ask the camera type directly; the aim sites would drift. */
    public static boolean isThirdPersonAllowed() {
        return thirdPersonAllowed;
    }

    /**
     * Call at the start of every frame, BEFORE updateSmoothedTilt. Frame conditions and mode
     * tracking live here because this is the one method {@code CameraMixin} calls
     * UNCONDITIONALLY: anywhere lower and a mode switch is missed while third person is off.
     */
    private static void applyTickState(float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        // A null player is passed on instead of returning early, so last frame's
        // conditions cannot linger.
        Conditions.collect(mc.player, partialTick, mc.options.getCameraType().isFirstPerson());

        thirdPersonAllowed = Conditions.thirdPersonAllowed();

        // Forgotten HERE, not in applyTiltSource: that sits behind the CameraMixin gates and
        // would never run in a frame where the mod is off.
        tiltSourceMod = null;
        sourceEyeOffset = Vec3.ZERO;

        boolean applying = shouldApplyTilt();
        if (applying && !wasApplyingTilt) {
            smoothedTilt.identity();
        }
        wasApplyingTilt = applying;

        boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        if (lastFirstPerson != null && lastFirstPerson.booleanValue() != firstPerson) {
            cameraModeChanged = true;
        }
        lastFirstPerson = firstPerson;
    }

    /**
     * The single resolve point for the tilt, foreign sources included. Camera, crosshair, rays,
     * projectiles and the server sync all read it further down the frame and must get ONE value;
     * resolve at apply time instead and the client draws one tilt while the server scores another.
     *
     * @param freeze holds the tilt (player airborne above a sub-level)
     */
    private static void slerpSmoothedTilt(@javax.annotation.Nullable Vector3f surfaceNormal,
                                          float deltaTime,
                                          float partialTick,
                                          boolean freeze) {
        boolean ours = !Conditions.baselineSkipped();

        // Freeze is ACS policy, so a mod that took the tilt took the freeze with it. Otherwise a
        // camera caught mid-jump stays tilted to the sub-level forever.
        if (!freeze || !ours) {
            Quaternionf target = (ours && surfaceNormal != null)
                    ? new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), surfaceNormal)
                    : new Quaternionf();

            float alpha = Config.SMOOTH_SPEED.get().floatValue();
            float t = 1f - (float) Math.pow(0.5, deltaTime / alpha);
            smoothedTilt.slerp(target, t);
        }

        // AFTER the smoothing, not instead of it: the source receives the computed tilt.
        applyTiltSource(surfaceNormal, deltaTime, partialTick);
    }

    /**
     * Hands the frame to a foreign source. The result goes straight into {@link #smoothedTilt},
     * which buys smoothing for free: when the source stops claiming, the ordinary slerp carries on
     * FROM wherever it left off, with no special case.
     */
    private static void applyTiltSource(@javax.annotation.Nullable Vector3f surfaceNormal,
                                        float deltaTime, float partialTick) {
        if (TiltSources.isEmpty()) return;

        // The only thing that cuts a source off: a cutscene must take the camera whoever is
        // tilting it.
        if (SuppressionLeases.isSuppressed()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Vec3 feet = feetPosition(player, partialTick);

        // The fallback is the eye, not the feet: should the snapshot go stale, the eye is wrong by
        // the camera height, the feet by the player's whole stature.
        Vec3 vanillaCamPos = FrameVanillaState.isFresh()
                ? FrameVanillaState.pos()
                : player.getEyePosition(partialTick);

        TiltSources.Winner winner = TiltSources.resolve(player, partialTick, deltaTime,
                surfaceNormal, new Quaternionf(smoothedTilt), isFirstPerson(),
                vanillaCamPos, feet);
        if (winner == null) return;

        smoothedTilt.set(winner.tilt());
        sourceEyeOffset = winner.eyeOffset();
        tiltSourceMod = winner.modId();
    }

    private static Vec3 feetPosition(LocalPlayer player, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, player.xOld, player.getX()),
                Mth.lerp(partialTick, player.yOld, player.getY()),
                Mth.lerp(partialTick, player.zOld, player.getZ()));
    }

    /**
     * Computes and applies in one go, inside {@code Camera#setup}. The two steps cannot be split
     * across the frame: eye-height smoothing (the crouch ease) lives in the {@code Camera} object
     * and is not updated until {@code setup} runs.
     */
    private static void applyToCamera(Camera camera, float partialTick) {
        updateWallScale(camera.getPosition(), partialTick);

        if (rotationActive()) {
            applyCameraRotation(camera);
        }
        if (posShiftActive()) {
            camera.setPosition(computeCameraPosition(camera.getPosition(), partialTick));
        }

        reportFrame(partialTick);
    }

    /**
     * Must be the LAST line of application: {@link #updateWallScale} runs at the start of the same
     * method, so reporting earlier hands out a previous-frame {@code tiltScale()}.
     */
    private static void reportFrame(float partialTick) {
        if (Conditions.isEmpty()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        boolean baselineActive = tiltSourceMod == null
                && !Conditions.baselineSkipped()
                && !SuppressionLeases.isSuppressed()
                && shouldApplyTilt();

        Conditions.report(player, partialTick, isFirstPerson(), tiltSourceMod, baselineActive,
                effectiveTilt(), effectiveEyeOffset(), wallScale);
    }

    /** The tilt as APPLIED: smoothed and scaled by wall proximity. */
    public static Quaternionf getSmoothedTilt() {
        return effectiveTilt();
    }

    /**
     * Scaled by the same wall factor as the tilt: {@code tiltScale()} promises one number scales
     * EVERYTHING, and an unscaled offset makes that a lie. {@code ZERO}, never {@code null}.
     */
    public static Vec3 effectiveEyeOffset() {
        Vec3 raw = sourceEyeOffset;
        if (raw.lengthSqr() == 0.0) return Vec3.ZERO;
        return wallScale >= 0.999f ? raw : raw.scale(wallScale);
    }

    /**
     * How far the tilt moves the eye, in WORLD space, or {@code null} to stay out. Every ray "from
     * the eye" needs this too, since {@code getViewVector} is tilted globally.
     *
     * <p>Recomputed rather than taken as {@code camera position - eye}: the camera updates in
     * {@code Camera#setup}, after the pick, so the difference would lag a frame.
     */
    @javax.annotation.Nullable
    public static Vec3 aimEyeOffset(float partialTick) {
        if (!Config.isLoaded()) return null;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;

        // No camera-mode check here: the arithmetic rotates the eye about the feet and never
        // looks at the camera position, and permission arrives via getPosTilt -> shouldApplyTilt.
        Quaternionf posTilt = com.playsi.aero_cam_sync.TiltAccess.getPosTilt(player);
        if (posTilt == null) return null;

        Vec3 rotation = com.playsi.aero_cam_sync.TiltAccess.eyeRotationDelta(
                player.getEyePosition(partialTick), player.getPosition(partialTick), posTilt);

        // Plus the source offset, or the ray leaves from a point the camera is not at.
        return rotation.add(com.playsi.aero_cam_sync.TiltAccess.sourceEyeOffset(player));
    }

    /** Wall proximity, 0..1. Stays 1 in third person and with collision off. */
    public static float tiltScale() {
        return wallScale;
    }

    @javax.annotation.Nullable
    public static String tiltSource() {
        return tiltSourceMod;
    }

    /**
     * {@code MODIFY_CAMERA_ROT} / {@code MODIFY_CAMERA_POS} must not cut a foreign tilt.
     * They split the ACS arithmetic into two halves; a foreign tilt is one value owned whole, and
     * switching off half of it gives a rotated view with an unshifted ray origin. API suppression
     * is the knob for that.
     */
    public static boolean rotationActive() {
        return tiltSourceMod != null || Config.MODIFY_CAMERA_ROT.get();
    }

    public static boolean posShiftActive() {
        return tiltSourceMod != null || Config.MODIFY_CAMERA_POS.get();
    }

    private static Quaternionf effectiveTilt() {
        if (wallScale >= 0.999f) return new Quaternionf(smoothedTilt);
        return new Quaternionf().slerp(smoothedTilt, wallScale);
    }

    /** Both halves at once, so modders get one concept and not two. */
    private static boolean collisionEnabled() {
        return Config.CAMERA_COLLISION.get() && !Conditions.cameraCollisionTakenOver();
    }

    private static boolean isFirstPerson() {
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private static void applyCameraRotation(Camera camera) {
        Quaternionf tilt    = effectiveTilt();
        Quaternionf vanilla = new Quaternionf(camera.rotation());
        tilt.mul(vanilla);
        camera.rotation().set(tilt);
    }

    /**
     * The LARGEST tilt scale (0..1) at which the camera sits in clear space. Shrinking is fast so
     * the camera cannot slip into a block; the return is gentle.
     *
     * <p>Not computed in third person. The search rests on the unchecked
     * invariant "scale 0, the eye position, is clear", which is false by construction there:
     * vanilla probes corners at 0.1 and stops on contact while the clear test here probes at 0.15, so
     * at {@code s = 0} it finds the wall vanilla pushed the camera against and the search
     * converges to 0. Safe to skip, because X-ray comes only from the SHIFT, which
     * {@code clampToCollision} still watches.
     */
    static void updateWallScale(Vec3 vanillaCamPos, float partialTick) {
        float target = 1.0f;

        // posShiftActive(), not the setting: under a foreign source the position shifts anyway.
        if (posShiftActive() && collisionEnabled() && isFirstPerson()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                double feetX = Mth.lerp(partialTick, player.xOld, player.getX());
                double feetY = Mth.lerp(partialTick, player.yOld, player.getY());
                double feetZ = Mth.lerp(partialTick, player.zOld, player.getZ());

                Vec3 eye = vanillaCamPos;
                ClientSubLevel sl = SubLevelTracker.getHeldSubLevel();
                Pose3dc pose = null;
                try { if (sl != null) pose = sl.renderPose(partialTick); }
                catch (Throwable ignored) { pose = null; }

                Vector3f eyeVector = new Vector3f(
                        (float) (eye.x - feetX),
                        (float) (eye.y - feetY),
                        (float) (eye.z - feetZ));

                if (cameraClear(player, eye, wallScaleCamPos(feetX, feetY, feetZ, eyeVector, 1.0f), sl, pose)) {
                    target = 1.0f; // s = 1 is clear: no wall nearby, no need to search
                } else {
                    // lo = 0 is the ASSUMPTION "the eye position is clear", not a check. When it
                    // is false the search converges to 0 and the tilt dies out entirely.
                    float lo = 0.0f;
                    float hi = 1.0f; // known to be blocked
                    for (int i = 0; i < 10; i++) {
                        float mid = (lo + hi) * 0.5f;
                        Vec3 cam = wallScaleCamPos(feetX, feetY, feetZ, eyeVector, mid);
                        if (cameraClear(player, eye, cam, sl, pose)) lo = mid; else hi = mid;
                    }
                    target = lo;
                }
            }
        }

        // INSTANTLY on a mode change: the descent takes ~5 frames, and third to first person near
        // a wall would leave the camera inside a block for all of them.
        if (cameraModeChanged) {
            cameraModeChanged = false;
            wallScale = target;
            return;
        }

        float dt = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        float smooth = Config.CAMERA_COLLISION_SMOOTH.get().floatValue();
        float halfLife = (target < wallScale) ? smooth : smooth * 1.5f;
        float a = (halfLife <= 1.0e-4f) ? 1f : 1f - (float) Math.pow(0.5, dt / halfLife);
        wallScale = Mth.lerp(a, wallScale, target);
    }

    /**
     * The source offset enters with the same {@code s} as the tilt, or the search would look for
     * clearance around a point the camera never occupies.
     */
    private static Vec3 wallScaleCamPos(double feetX, double feetY, double feetZ,
                                        Vector3f eyeVector, float s) {
        Vector3f off = new Quaternionf().slerp(smoothedTilt, s).transform(new Vector3f(eyeVector));
        Vec3 extra = sourceEyeOffset;
        return new Vec3(
                feetX + off.x + extra.x * s,
                feetY + off.y + extra.y * s,
                feetZ + off.z + extra.z * s);
    }

    private static boolean cameraClear(LocalPlayer player, Vec3 eye, Vec3 cam,
                                       ClientSubLevel sl, Pose3dc pose) {
        Level level = player.level();

        if (blockedWorld(level, player, eye, cam)) return false;
        if (blockedSub(sl, pose, player, eye, cam)) return false;

        // 8 diagonal corners rather than 6 axes: axial rays miss a block the camera meets
        // corner-on, and wallScale then called the position clear at certain tilt angles.
        final double R = 0.15;
        for (int i = 0; i < 8; i++) {
            double sx = (i & 1) != 0 ? R : -R;
            double sy = (i & 2) != 0 ? R : -R;
            double sz = (i & 4) != 0 ? R : -R;
            Vec3 to = cam.add(sx, sy, sz);
            if (blockedWorld(level, player, cam, to)) return false;
            if (blockedSub(sl, pose, player, cam, to)) return false;
        }
        return true;
    }

    private static boolean blockedWorld(Level level, LocalPlayer player, Vec3 from, Vec3 to) {
        ClipContext ctx = new ClipContext(from, to,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
        ((ClipContextExtension) ctx).sable$setDoNotProject(true);
        // The net must stay out, or collision measures against its own output.
        ClipNet.suppress();
        try {
            return level.clip(ctx).getType() != HitResult.Type.MISS;
        } finally {
            ClipNet.resume();
        }
    }

    private static boolean blockedSub(ClientSubLevel sl, Pose3dc pose, LocalPlayer player,
                                      Vec3 from, Vec3 to) {
        if (sl == null || pose == null) return false;
        Vec3 lf = pose.transformPositionInverse(from);
        Vec3 lt = pose.transformPositionInverse(to);
        ClipContext ctx = new ClipContext(lf, lt,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
        ((ClipContextExtension) ctx).sable$setDoNotProject(true);
        ClipNet.suppress();
        try {
            return sl.getLevel().clip(ctx).getType() != HitResult.Type.MISS;
        } finally {
            ClipNet.resume();
        }
    }

    /** @param vanillaCamPos camera position before ACS touches it */
    static Vec3 computeCameraPosition(Vec3 vanillaCamPos, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return vanillaCamPos;

        double feetX = Mth.lerp(partialTick, player.xOld, player.getX());
        double feetY = Mth.lerp(partialTick, player.yOld, player.getY());
        double feetZ = Mth.lerp(partialTick, player.zOld, player.getZ());

        Vector3f offset = effectiveTilt().transform(new Vector3f(
                (float)(vanillaCamPos.x - feetX),
                (float)(vanillaCamPos.y - feetY),
                (float)(vanillaCamPos.z - feetZ)));

        // Added AFTER the rotation and BEFORE the clamp. This formula must match
        // TiltContextImpl.cameraPosFor(), which a source uses to work out the difference to
        // return; let them drift and a mod measures from a point the camera never reaches.
        Vec3 extra = effectiveEyeOffset();

        double targetX = feetX + offset.x + extra.x;
        double targetY = feetY + offset.y + extra.y;
        double targetZ = feetZ + offset.z + extra.z;

        // Must NOT depend on camera mode: this is the only X-ray protection for the shoulder
        // rotation, and vanilla computed its zoom BEFORE that rotation, promising nothing about
        // the rotated point.
        if (collisionEnabled()) {
            Vec3 clamped = clampToCollision(player, vanillaCamPos,
                    new Vec3(targetX, targetY, targetZ), partialTick);
            targetX = clamped.x;
            targetY = clamped.y;
            targetZ = clamped.z;
        }

        return new Vec3(targetX, targetY, targetZ);
    }

    /** {@code anchor} is the eye, the known-safe point. Eight offset rays cover the near plane. */
    private static Vec3 clampToCollision(LocalPlayer player, Vec3 anchor, Vec3 desired, float partialTick) {
        Vec3 delta = desired.subtract(anchor);
        double dist = delta.length();
        if (dist < 1.0e-4) return desired;

        float maxDist = collisionMaxDist(player, anchor, desired, partialTick);
        if (maxDist >= dist) return desired;
        return anchor.add(delta.scale(maxDist / dist));
    }

    private static float collisionMaxDist(LocalPlayer player, Vec3 anchor, Vec3 desired, float partialTick) {
        Level level = player.level();
        Vec3 delta = desired.subtract(anchor);
        double dist = delta.length();
        if (dist < 1.0e-4) return 0f;

        ClientSubLevel subLevel = SubLevelTracker.getHeldSubLevel();
        Pose3dc subPose = null;
        try {
            if (subLevel != null) subPose = subLevel.renderPose(partialTick);
        } catch (Throwable ignored) { subPose = null; }

        float maxDist = (float) dist;

        // The net must stay out, or the wall check measures from an already shifted camera.
        ClipNet.suppress();
        try {
            for (int i = 0; i < 8; i++) {
                Vec3 corner = new Vec3(
                        (i & 1) * 2 - 1,
                        (i >> 1 & 1) * 2 - 1,
                        (i >> 2 & 1) * 2 - 1
                ).scale(0.1);

                Vec3 from = anchor.add(corner);
                Vec3 to = desired.add(corner);

                // WORLD. Sable @Overwrites clip and projects an origin inside a contraption AABB
                // into the far raft, turning the ray to nonsense. doNotProject restores vanilla.
                ClipContext ctx = new ClipContext(from, to,
                        ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
                ((ClipContextExtension) ctx).sable$setDoNotProject(true);
                HitResult hit = level.clip(ctx);
                if (hit.getType() != HitResult.Type.MISS) {
                    float d = (float) hit.getLocation().distanceTo(anchor);
                    if (d < maxDist) maxDist = d;
                }

                // SUB-LEVEL. The pose is a rigid transform, so local distance == world distance.
                if (subPose != null) {
                    Vec3 lFrom = subPose.transformPositionInverse(from);
                    Vec3 lTo = subPose.transformPositionInverse(to);
                    ClipContext lctx = new ClipContext(lFrom, lTo,
                            ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
                    ((ClipContextExtension) lctx).sable$setDoNotProject(true);
                    HitResult lhit = subLevel.getLevel().clip(lctx);
                    if (lhit.getType() != HitResult.Type.MISS) {
                        float d = (float) lhit.getLocation().distanceTo(lFrom);
                        if (d < maxDist) maxDist = d;
                    }
                }
            }
        } finally {
            ClipNet.resume();
        }

        return maxDist;
    }
}