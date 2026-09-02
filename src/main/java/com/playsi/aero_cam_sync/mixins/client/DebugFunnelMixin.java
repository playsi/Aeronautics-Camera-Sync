package com.playsi.aero_cam_sync.mixins.client;

import com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.PickDiagnostics;
import com.playsi.aero_cam_sync.client.aim.PickScope;
import dev.ryanhcode.sable.ActiveSableCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostics: who asks for the player's eye OUTSIDE the pick window.
 *
 * <p>The hypothesis under test: {@code @WrapMethod} moves the body of {@code pick(F)V} into a
 * synthetic method, so a foreign {@code @Inject(TAIL)} applied later lands on the wrapper and
 * runs after {@code PickScope.close()}. Such a mod gets the UNTILTED eye and misses. If the
 * hypothesis holds, its class surfaces here.
 *
 * <p>Sable's render paths (light probe, entity render offset) call the funnel every frame and land
 * in the log too, which is why the caller is printed and no more than once a second.
 */
@Mixin(value = ActiveSableCompanion.class, remap = false)
public abstract class DebugFunnelMixin {

    @Inject(method = "getEyePositionInterpolated", at = @At("HEAD"))
    private void aero$logOutsideScope(Entity entity, float partialTicks,
                                      CallbackInfoReturnable<Vec3> cir) {
        if (!ClientTiltAccess.isDebugMessages()) return;

        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || entity != mc.player) return;

        // EVERY consumer of the funnel is printed, not only those outside the window: the full
        // list is what decides whether the gate can be inverted (tilt by default, excluding render
        // paths). Each unique caller once.
        PickDiagnostics.logOnce(PickScope.isActive() ? "funnel IN scope" : "funnel OUT of scope",
                PickDiagnostics.caller());
    }
}
