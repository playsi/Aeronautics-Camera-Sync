package com.playsi.aero_cam_sync.mixins.client;

import com.playsi.aero_cam_sync.client.aim.RenderEyeScope;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks the two render methods inside which Sable's funnel must return the real eye.
 *
 * <p>Both are vanilla methods, interesting only because Sable inserts hooks into them and calls
 * {@code getEyePositionInterpolated} from there: {@code getPackedLightCoords} is the light probe and
 * {@code shouldRender} is visibility selection. The vanilla method is wrapped whole, so Sable's hook
 * is guaranteed to land inside the window whatever the mixin application order.
 *
 * <p>The rationale for the list, and its cost, are in {@link RenderEyeScope}.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRenderEyeMixin {

    @Inject(method = "getPackedLightCoords", at = @At("HEAD"))
    private void aero$lightProbeEnter(CallbackInfoReturnable<Integer> cir) {
        RenderEyeScope.enter();
    }

    @Inject(method = "getPackedLightCoords", at = @At("RETURN"))
    private void aero$lightProbeExit(CallbackInfoReturnable<Integer> cir) {
        RenderEyeScope.exit();
    }

    @Inject(method = "shouldRender", at = @At("HEAD"))
    private void aero$visibilityEnter(CallbackInfoReturnable<Boolean> cir) {
        RenderEyeScope.enter();
    }

    @Inject(method = "shouldRender", at = @At("RETURN"))
    private void aero$visibilityExit(CallbackInfoReturnable<Boolean> cir) {
        RenderEyeScope.exit();
    }
}
