package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.AeroCamSync;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostics: the last link in the chain, which cell the block actually lands in.
 * {@code getClickedPos()} is the hit position, or that position {@code .relative(face)} when the
 * clicked block is not replaceable, so with the right position but the wrong face the block moves
 * to the neighbouring cell.
 */
@Mixin(BlockItem.class)
public abstract class DebugPlaceMixin {

    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"))
    private void aero$logPlace(BlockPlaceContext context, CallbackInfoReturnable<?> cir) {
        AeroCamSync.LOGGER.info(
                "[AeroCamSync] {} place: clickedPos={} face={} | horizDir={} rot={}",
                context.getLevel().isClientSide ? "CLIENT" : "SERVER",
                context.getClickedPos().toShortString(),
                context.getClickedFace(),
                context.getHorizontalDirection(),
                String.format("%.1f", context.getRotation()));
    }
}
