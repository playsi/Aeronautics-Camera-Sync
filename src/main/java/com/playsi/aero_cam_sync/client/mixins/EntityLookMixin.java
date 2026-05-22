package com.playsi.aero_cam_sync.client.mixins;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.utils.CameraController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityLookMixin {

    @Inject(method = "getViewVector", at = @At("RETURN"), cancellable = true)
    private void tiltViewVector(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (!((Object)this instanceof LocalPlayer)) return;
        if (!Config.MOD_ENABLED.get()) return;
        if (!Config.MODIFY_CAMERA_ROT.get()) return;
        if (!CameraController.shouldApplyTilt()) return;
        Vec3 vanilla = cir.getReturnValue();
        Vector3f v = new Vector3f(
                (float)vanilla.x, (float)vanilla.y, (float)vanilla.z
        );
        CameraController.getSmoothedTilt().transform(v);
        cir.setReturnValue(new Vec3(v.x, v.y, v.z));
    }

    @Inject(method = "getLookAngle", at = @At("RETURN"), cancellable = true)
    private void tiltLookAngle(CallbackInfoReturnable<Vec3> cir) {
        if (!((Object)this instanceof LocalPlayer)) return;
        if (!Config.MOD_ENABLED.get()) return;
        if (!Config.MODIFY_CAMERA_ROT.get()) return;
        if (!CameraController.shouldApplyTilt()) return;

        Vec3 vanilla = cir.getReturnValue();
        Vector3f v = new Vector3f(
                (float)vanilla.x, (float)vanilla.y, (float)vanilla.z
        );
        CameraController.getSmoothedTilt().transform(v);
        cir.setReturnValue(new Vec3(v.x, v.y, v.z));
    }
}
