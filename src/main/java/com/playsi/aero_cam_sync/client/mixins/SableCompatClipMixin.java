package com.playsi.aero_cam_sync.client.mixins;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.utils.ClipShifter;
import com.playsi.aero_cam_sync.client.utils.CameraController;
import com.playsi.aero_cam_sync.client.utils.LevelClipMixinState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockGetter.class, priority = 1200)
public interface SableCompatClipMixin {

    @Inject(method = "clip", at = @At("HEAD"), cancellable = true)
    default void shiftClipForCameraTilt(ClipContext context,
                                        CallbackInfoReturnable<BlockHitResult> cir) {
        if (!Config.isLoaded()) return;
        if (LevelClipMixinState.inTiltedClip) return;
        if (!Config.MOD_ENABLED.get()) return;
        if (!CameraController.shouldApplyTilt()) return;
        if (!Config.MODIFY_CAMERA_POS.get()) return;

        try {
            ClipShifter.tryShift((BlockGetter) this, context, cir);
        } catch (Throwable ignored) { }
    }
}