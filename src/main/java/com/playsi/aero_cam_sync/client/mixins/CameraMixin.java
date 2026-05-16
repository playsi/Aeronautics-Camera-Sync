package com.playsi.aero_cam_sync.client.mixins;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import com.playsi.aero_cam_sync.client.utils.CameraController;
import com.playsi.aero_cam_sync.client.utils.SubLevelTracker;
import com.playsi.aero_cam_sync.client.utils.SurfaceRaycaster;
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

@Mixin(value = Camera.class, priority = 10000)
public abstract class CameraMixin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void applyTerrainTilt(
            BlockGetter level, Entity entity,
            boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {

        if (!Config.MOD_ENABLED.get()) return;
        if (!CameraController.shouldApplyTilt()) return;

        Minecraft mc = Minecraft.getInstance();
        float deltaTime = mc.getTimer().getRealtimeDeltaTicks();

        ClientSubLevel subLevel = SubLevelTracker.getClientSubLevel(mc.player);

        DebugRayRenderer.clear();

        Vector3f surfaceNormal = null;
        if (subLevel != null) {
            Pose3dc pose = subLevel.renderPose(partialTick);
            surfaceNormal = SurfaceRaycaster.getSurfaceNormal(subLevel, pose);

            if (surfaceNormal == null && Config.DROP_CACHE_ON_ALL_MISS.get()) {
                SubLevelTracker.invalidateCache();
            }
        }

        CameraController.updateSmoothedTilt(surfaceNormal, deltaTime, false);
        CameraController.applyTiltToCamera((Camera)(Object) this, partialTick);
    }
}