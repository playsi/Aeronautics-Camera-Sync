package com.playsi.aero_cam_sync.mixins.client;

import com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess;
import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.PickDiagnostics;
import com.playsi.aero_cam_sync.client.aim.PickScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostics: records the result of EVERY {@code pick(F)} call, not only the one wrapped by
 * {@link PickScopeMixin}.
 *
 * <p>The hypothesis under test: the pick runs more than once per frame and the last call bypasses
 * the window, leaving {@code mc.hitResult} vanilla by click time even though the pick was correct.
 * The RETURN inject lands inside the call, so {@code PickScope.isActive()} here honestly reports
 * whether that particular call was in the window.
 */
@Mixin(GameRenderer.class)
public abstract class DebugPickTailMixin {

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void aero$recordPickTail(float partialTick, CallbackInfo ci) {
        PickDiagnostics.picksThisFrame++;
        PickDiagnostics.lastPickTail = (PickScope.isActive() ? "scoped:" : "UNSCOPED:")
                + aero$describeTail(Minecraft.getInstance().hitResult);

        // The second pick of the frame is still unidentified, and it has twice been the cause
        // (placement face, pose for foreign rays). Print every unique caller.
        if (ClientTiltAccess.isDebugMessages()) {
            PickDiagnostics.logOnce("pick call", PickDiagnostics.caller());
        }
    }

    /**
     * The prefixed name is mandatory: private mixin methods merge into the target class, and while
     * this one was simply {@code describe} it collided with the identically named method of
     * {@code PickScopeMixin}, giving a method overwrite conflict in the log and losing the
     * {@code BLOCK@} prefix from the output. Production has no conflict anyway (the mixin does not
     * get there), but a dev run showed it on every launch.
     */
    private static String aero$describeTail(HitResult hit) {
        if (hit == null || hit.getType() == HitResult.Type.MISS) return "MISS";
        if (hit instanceof BlockHitResult block) {
            return block.getBlockPos().toShortString() + " " + block.getDirection();
        }
        return hit.getType().toString();
    }
}
