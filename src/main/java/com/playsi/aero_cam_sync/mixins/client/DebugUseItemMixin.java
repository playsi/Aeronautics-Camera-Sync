package com.playsi.aero_cam_sync.mixins.client;

import com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess;
import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.PickDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostics: prints which hit actually goes into the placement packet. The
 * {@code PickScopeMixin} log is taken at the end of the frame while the click is handled on the
 * next tick, and {@code mc.hitResult} can be overwritten in between.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class DebugUseItemMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void aero$logUseItemOn(LocalPlayer player, InteractionHand hand,
                                   BlockHitResult hit, CallbackInfoReturnable<?> cir) {
        if (!ClientTiltAccess.isDebugMessages()) return;

        AeroCamSync.LOGGER.info(
                "[AeroCamSync] useItemOn: sent={} {} | scope={} picks={} lastPick={} | frameTilted={} | frameVanilla={} | shift={}",
                hit.getBlockPos().toShortString(),
                hit.getDirection(),
                PickDiagnostics.lastScope,
                PickDiagnostics.picksThisFrame,
                PickDiagnostics.lastPickTail,
                PickDiagnostics.lastTilted,
                PickDiagnostics.lastVanilla,
                String.format("%.3f", Minecraft.getInstance().gameRenderer.getMainCamera()
                        .getPosition().distanceTo(player.getEyePosition(1.0f))));
    }

}
