package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class ClipShifter {

    public static void tryShift(BlockGetter level, ClipContext context,
                                CallbackInfoReturnable<BlockHitResult> cir) {
        // Только главный поток (см. SableCompatClipMixin): фоновые лучи звука Sound Physics
        // Perfected трогать нельзя, иначе гонка по статическому guard'у и зависание.
        if (!Minecraft.getInstance().isSameThread()) return;
        if (LevelClipMixinState.inTiltedClip) return;
        if (context == LevelClipMixinState.tiltedContext) return;
        if (!(level instanceof ClientLevel)) return;

        try {
            if (!Config.MOD_ENABLED.get()) return;
            if (!Config.MODIFY_CAMERA_POS.get()) return;
        } catch (IllegalStateException e) { return; }

        if (!CameraController.shouldApplyTilt()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        Vec3 eyePos = player.getEyePosition(1.0f);
        if (context.getFrom().distanceToSqr(eyePos) > 16.0) return;

        Vec3 delta = mc.gameRenderer.getMainCamera().getPosition()
                .subtract(player.getEyePosition(1.0f));
        if (delta.lengthSqr() < 1e-6) return;

        ClipContext newContext = new ClipContext(
                context.getFrom().add(delta),
                context.getTo().add(delta),
                context.block,
                context.fluid,
                player
        );

        LevelClipMixinState.inTiltedClip = true;
        LevelClipMixinState.tiltedContext = newContext;
        try {
            BlockHitResult result = level.clip(newContext);
            cir.setReturnValue(result);
            cir.cancel();
        } finally {
            LevelClipMixinState.inTiltedClip = false;
            LevelClipMixinState.tiltedContext = null;
        }
    }
}