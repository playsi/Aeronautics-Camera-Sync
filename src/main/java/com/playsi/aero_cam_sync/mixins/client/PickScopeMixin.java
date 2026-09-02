package com.playsi.aero_cam_sync.mixins.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.ClipNet;
import com.playsi.aero_cam_sync.TiltAccess;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import com.playsi.aero_cam_sync.client.debug.PickDiagnostics;
import com.playsi.aero_cam_sync.client.tilt.CameraController;
import com.playsi.aero_cam_sync.client.aim.PickScope;
import com.playsi.aero_cam_sync.client.aim.RenderEyeScope;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Opens a {@link PickScope} around the WHOLE of {@code GameRenderer#pick(F)}, so everything inside
 * a pick lands in the window and nothing races for the last write to {@code mc.hitResult}.
 *
 * <p>The method, not the call site in {@code renderLevel}: {@code pick(F)} is called TWICE
 * per frame and the second call bypasses that site, computing from an untilted eye and overwriting
 * {@code mc.hitResult} last.
 *
 * <p>The origin is the main camera position from the PREVIOUS frame, since {@code pick(F)} runs
 * before {@code Camera#setup}. Computing the tilt earlier in the frame was tried and reverted: it
 * broke the crouch ease and took the tilt write out from under the main-camera guard.
 */
@Mixin(GameRenderer.class)
public abstract class PickScopeMixin {

    @WrapMethod(method = "pick(F)V")
    private void aero$scopedPick(float partialTick, Operation<Void> original) {
        PickDiagnostics.resetPickCount();

        // An exception that left the "real eye" window open would disable the tilt permanently and
        // silently; a pick is the natural reset point.
        RenderEyeScope.reset();

        Vec3 origin = pickOrigin(partialTick);
        Vec3 offset = origin == null ? null : tiltOffset(partialTick);
        if (origin == null || offset == null) {
            if (offset == null && origin != null) PickDiagnostics.recordScope("off:no-tilt");
            original.call(partialTick);
            // Still recorded, or diagnostics keep last frame's value and the click looks
            // overwritten.
            recordFrame(partialTick, null);
            return;
        }

        PickDiagnostics.recordScope("on");
        submitDebugRays(origin, partialTick);

        PickScope.open(origin);

        // Sable pushes renderPose ONLY for the frame call, so the tick pick, whose result a click
        // uses, would run against a different pose than the sub-level is drawn in. Pushing it
        // again when Sable already did is harmless; the stack just gets one deeper.
        LevelPoseProviderExtension poses = (LevelPoseProviderExtension) Minecraft.getInstance().level;
        poses.sable$pushPoseSupplier(sub -> ((ClientSubLevel) sub).renderPose(partialTick));
        try {
            original.call(partialTick);
        } finally {
            poses.sable$popPoseSupplier();
            recordFrame(partialTick, origin);
            PickScope.close();
        }
    }

    /**
     * {@code subs=0} means the window opened but Sable's funnel was never reached, so the pick
     * stayed vanilla. {@code shift} must be well above zero under tilt.
     */
    private static void recordFrame(float partialTick, Vec3 origin) {
        if (!PickDiagnostics.enabled()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // The sub-level drifts, so pick and click compare only within one frame.
        PickDiagnostics.lastTilted = describe(Minecraft.getInstance().hitResult);
        PickDiagnostics.lastVanilla = describe(vanillaPick(player, partialTick));

        if (++frameCounter % 60 != 0) return;

        Vec3 eye = player.getEyePosition(partialTick);
        AeroCamSync.LOGGER.info(
                "[AeroCamSync] pick: scope={} subs={} shift={} tiltErr={}° camDelta={} tilted={} vanilla={}",
                PickDiagnostics.lastScope,
                PickScope.substitutions(),
                origin == null ? "-" : String.format("%.3f", origin.distanceTo(eye)),
                tiltError(player, partialTick),
                PickDiagnostics.lastEyeDelta,
                PickDiagnostics.lastTilted,
                PickDiagnostics.lastVanilla);
    }

    /**
     * The angle between the applied tilt and the sub-level's real pose. Code that unwinds the ray
     * back into sub-level space takes the REAL orientation off it, and the remainder is its miss:
     * invisible on a block pick, fatal on a panel button.
     */
    private static String tiltError(LocalPlayer player, float partialTick) {
        var sub = dev.ryanhcode.sable.Sable.HELPER.getTrackingOrVehicleSubLevel(player);
        if (!(sub instanceof ClientSubLevel client)) return "-";

        Quaternionf ours = CameraController.getSmoothedTilt();
        var deck = client.renderPose(partialTick).orientation();

        double dot = Math.abs(ours.x * deck.x() + ours.y * deck.y()
                + ours.z * deck.z() + ours.w * deck.w());
        return String.format("%.2f", Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot))));
    }

    /** The reference ray: UNTILTED eye, raw direction, outside {@link PickScope}. */
    private static BlockHitResult vanillaPick(LocalPlayer player, float partialTick) {
        Vec3 from = player.getEyePosition(partialTick);
        Vec3 dir = player.calculateViewVector(player.getViewXRot(partialTick),
                player.getViewYRot(partialTick));
        Vec3 to = from.add(dir.scale(player.blockInteractionRange()));
        // The net must stay out: its filter matches this origin exactly, and a shifted reference
        // is not a reference.
        ClipNet.suppress();
        try {
            return player.level().clip(new ClipContext(
                    from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        } finally {
            ClipNet.resume();
        }
    }

    /** Includes the face, so a whole-block divergence reads differently from a face one. */
    private static String describe(HitResult hit) {
        if (hit == null || hit.getType() == HitResult.Type.MISS) return "MISS";
        if (hit instanceof BlockHitResult block) {
            return "BLOCK@" + block.getBlockPos().toShortString() + " " + block.getDirection();
        }
        return hit.getType().toString();
    }

    private static int frameCounter = 0;

    /**
     * The ray origin, or {@code null} to leave the pick fully vanilla. The conditions must match
     * those under which the camera tilts, or the ray drifts from the crosshair.
     */
    private static Vec3 pickOrigin(float partialTick) {
        // The client config spec may not be loaded on early frames (Issue #19).
        if (!Config.isLoaded()) return deny("config");
        // Not the setting directly: under a foreign source it does not cut the tilt, so reading it
        // here would move the camera while the pick stayed vanilla.
        if (!Config.MOD_ENABLED.get() && CameraController.tiltSource() == null) return deny("disabled");
        // posShiftActive(), NOT the setting, for the same reason.
        if (!CameraController.posShiftActive()) return deny("no-pos");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return deny("no-player");
        // Before shouldApplyTilt(), which holds the same gate, so diagnostics answer the most
        // common question with something better than "no-tilt-state".
        if (!mc.options.getCameraType().isFirstPerson()
                && !CameraController.isThirdPersonAllowed()
                && CameraController.tiltSource() == null) return deny("third-person");
        if (!CameraController.shouldApplyTilt()) return deny("no-tilt-state");

        // First person: the camera position IS the rotated eye, already collision-clamped.
        if (mc.options.getCameraType().isFirstPerson()) {
            return mc.gameRenderer.getMainCamera().getPosition();
        }

        // Third person: the camera sits BEHIND the player and cannot be the ray origin, so the
        // point it coincides with in first person is computed instead.
        Vec3 offset = tiltOffset(partialTick);
        if (offset == null) return deny("no-tilt");
        return mc.player.getEyePosition(partialTick).add(offset);
    }

    private static Vec3 deny(String reason) {
        PickDiagnostics.recordScopeDenied(reason);
        return null;
    }

    /** No gates here: {@link #pickOrigin(float)} holds them, once per pick. */
    private static Vec3 tiltOffset(float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;

        Quaternionf posTilt = TiltAccess.getPosTilt(player);
        if (posTilt == null) return null;

        // Third person computes the ray start here, and without the source offset it would
        // diverge from the camera by exactly that much.
        return TiltAccess.eyeRotationDelta(
                        player.getEyePosition(partialTick), player.getPosition(partialTick), posTilt)
                .add(TiltAccess.sourceEyeOffset(player));
    }

    private static void submitDebugRays(Vec3 origin, float partialTick) {
        if (!Config.DEBUG_PICK_RAYS.get()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Vec3 vanilla = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick).scale(player.blockInteractionRange());
        DebugRayRenderer.submitPickRay(vanilla, vanilla.add(look), 0.5f, 0.5f, 0.5f);

        // Yellow is the real origin. Drawing the camera position would show a different ray.
        Vec3 offset = tiltOffset(partialTick);
        Vec3 start = offset == null ? vanilla : vanilla.add(offset);
        DebugRayRenderer.submitPickRay(start, start.add(look), 1.0f, 1.0f, 0.0f);
    }
}
