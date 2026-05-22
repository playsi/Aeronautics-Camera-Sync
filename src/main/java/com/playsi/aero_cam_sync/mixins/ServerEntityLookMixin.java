package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.ServerTiltStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ServerEntityLookMixin {

    @Inject(method = "getViewVector", at = @At("RETURN"), cancellable = true)
    private void serverTiltViewVector(float partialTick,
                                      CallbackInfoReturnable<Vec3> cir) {

        if ((Object) this instanceof ServerPlayer sp) {
            AeroCamSync.LOGGER.info("[DEBUG] serverTiltLookAngle called for ServerPlayer: {}", sp.getName().getString());
            AeroCamSync.LOGGER.info("[DEBUG] tilt in store: {}", ServerTiltStore.get(sp.getUUID()));
        }

        if (!((Object) this instanceof ServerPlayer sp)) return;

        Quaternionf tilt = ServerTiltStore.get(sp.getUUID());
        if (tilt == null) return;

        Vec3 vanilla = cir.getReturnValue();
        Vector3f v = new Vector3f((float) vanilla.x, (float) vanilla.y, (float) vanilla.z);
        tilt.transform(v);
        cir.setReturnValue(new Vec3(v.x, v.y, v.z));
    }

    @Inject(method = "getLookAngle", at = @At("RETURN"), cancellable = true)
    private void serverTiltLookAngle(CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof ServerPlayer sp)) return;

        Quaternionf tilt = ServerTiltStore.get(sp.getUUID());
        if (tilt == null) return;

        Vec3 vanilla = cir.getReturnValue();
        Vector3f v = new Vector3f((float) vanilla.x, (float) vanilla.y, (float) vanilla.z);
        tilt.transform(v);
        cir.setReturnValue(new Vec3(v.x, v.y, v.z));
    }
}