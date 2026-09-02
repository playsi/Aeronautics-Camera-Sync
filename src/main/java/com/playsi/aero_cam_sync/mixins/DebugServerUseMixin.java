package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.AeroCamSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostics: prints the hit the server handles the placement with, to tell "the client sent the
 * wrong thing" apart from "the server recomputed it itself".
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class DebugServerUseMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void aero$logServerUseItemOn(ServerPlayer player, Level level, ItemStack stack,
                                         InteractionHand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<?> cir) {
        AeroCamSync.LOGGER.info(
                "[AeroCamSync] SERVER useItemOn: hit={} {} | item={}",
                hit.getBlockPos().toShortString(),
                hit.getDirection(),
                stack.getItem());
    }
}
