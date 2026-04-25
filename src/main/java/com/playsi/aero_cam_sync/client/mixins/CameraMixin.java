package com.playsi.aero_cam_sync.client.mixins;

import com.playsi.aero_cam_sync.client.Config;
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

import static com.playsi.aero_cam_sync.client.CameraUtils.*;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void applyTerrainTilt(
            BlockGetter level, Entity entity,
            boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {

        if (!Config.MOD_ENABLED.get()) return;
        if (!shouldApplyTilt()) return;

        ClientSubLevel subLevel = getClientSubLevel(Minecraft.getInstance().player);

        if (subLevel == null) {
            updateSmoothedTilt(null);
            applyTiltToCamera((Camera) (Object) this);
            return;
        }

        Pose3dc pose = subLevel.renderPose(partialTick);
        Vector3f surfaceNormal = getSurfaceNormal(subLevel, pose);

        updateSmoothedTilt(surfaceNormal);
        applyTiltToCamera((Camera) (Object) this);
    }
}