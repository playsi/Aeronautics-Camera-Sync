package com.playsi.aero_cam_sync.mixins.client;

import com.playsi.aero_cam_sync.SideManager;
import com.playsi.aero_cam_sync.apiimpl.SuppressionLeases;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.CameraDriverProbe;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import com.playsi.aero_cam_sync.client.debug.SubLevelHandoverProbe;
import com.playsi.aero_cam_sync.client.tilt.BlacklistHandle;
import com.playsi.aero_cam_sync.client.tilt.CameraController;
import com.playsi.aero_cam_sync.client.tilt.DeckOrientation;
import com.playsi.aero_cam_sync.client.sublevel.SubLevelThresholds;
import com.playsi.aero_cam_sync.client.sublevel.SubLevelTracker;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 1300)
public abstract class CameraMixin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void applyTerrainTilt(
            BlockGetter level, Entity entity,
            boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {

        Minecraft mc = Minecraft.getInstance();

        // MAIN camera only: some screens render a second view with their own camera, and one
        // recomputing the global wallScale from its own position jitters the main view. A WRITE
        // HANDLE rather than a boolean, so that stays true even if this early exit is rewritten.
        CameraController.Frame frame = CameraController.forMainCamera((Camera) (Object) this);
        if (frame == null) return;

        // HERE, before any tilt is applied and before every other exit: the API must return untilted
        // values even in frames with no tilt, and rotating back cannot recover them, because the
        // applied tilt is scaled by wallScale and that changes during the frame.
        frame.captureVanilla((Camera) (Object) this);

        if (CameraDriverProbe.DEV) {
            CameraDriverProbe.sample((Camera) (Object) this, entity, partialTick);
        }

        // Must stay HERE, behind the main-camera guard. Moving it to the start of the frame failed
        // twice: getEyePositionInterpolated takes the RAW eye height and kills the crouch ease, and
        // the write left this guard so a secondary camera corrupted shared state again.
        frame.tickApplyState(partialTick);

        if (mc.player == null) return;

        float deltaTime = mc.getTimer().getRealtimeDeltaTicks();

        // Vehicle, the mod toggle and camera mode live HERE rather than gating the method: all
        // three are ACS reasons not to apply the ACS tilt, and in the mechanism gate they also
        // switched off applyTiltSource, so a mod whose whole scenario IS a vehicle could not claim
        // a frame at any priority. Suppression is the one exception, killing both.
        //
        // Computed BEFORE the sub-level is chosen: choosing votes with a ring of rays, and there is
        // no point paying ten clips in a frame with no tilt.
        //
        // Auto-disable for projectiles is needed ONLY on a server without the mod, which cannot
        // turn the projectile itself.
        boolean banned =
                !Config.MOD_ENABLED.get()
                || !(CameraController.isThirdPersonAllowed() || mc.options.getCameraType().isFirstPerson())
                || (mc.player != null && mc.player.getVehicle() != null)
                || SuppressionLeases.isSuppressed()
                || (Config.CLIENT_BLACKLIST_ENABLED.get() &&
                        BlacklistHandle.holdBannedItem(Config.CLIENT_BLACKLIST_IDS.get()))
                || (Config.AUTO_DISABLE_FOR_RAYCAST_ITEMS.get() &&
                        SideManager.isClientOnly() &&
                        BlacklistHandle.holdRaycastItem());

        // The ray ring lives inside resolve, so the debug ray list is cleared BEFORE it.
        DebugRayRenderer.clear();

        ClientSubLevel subLevel = SubLevelTracker.resolve(mc.player, !banned);

        SubLevelHandoverProbe.sample(subLevel, partialTick);

        Vector3f surfaceNormal = null;

        // TWO DIFFERENT QUESTIONS, once answered by one path. "Nothing underfoot" and "too steep"
        // both produced a null normal and both dropped the sub-level choice, so rolling past the
        // threshold made the mod forget the sub-level the player is still standing on and the tilt
        // never came back until landing. Presence is decided by votes inside resolve; only the
        // angle is left here.
        if (!banned && subLevel != null && SubLevelThresholds.passes(subLevel)) {
            // Sable can throw on the pose, when the sub-level moves out from under the player
            // between frames. Any failure here means "no source", so an ease back to the horizon,
            // never a jerk and never an exception escaping: this is inside Camera#setup.
            Pose3dc pose;
            try {
                pose = subLevel.renderPose(partialTick);
            } catch (Throwable ignored) {
                pose = null;
            }
            if (pose != null) {
                surfaceNormal = DeckOrientation.targetUp(pose);
            }
        }

        frame.updateSmoothedTilt(surfaceNormal, deltaTime, partialTick, false);
        frame.applyTiltToCamera((Camera)(Object) this, partialTick);
    }
}
