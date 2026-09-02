package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.ClipNet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The net's entry point; see {@link ClipNet} for all the logic and the rationale for the filter.
 *
 * <p>It sits on {@code BlockGetter#clip}, because that interface default method is what everyone
 * calls, vanilla and mods alike ({@code Level} has no override of its own).
 *
 * <p>{@code priority = 1200}: injectors apply in descending priority, and on HEAD this must
 * come before Sable's interception, so Sable receives an already shifted ray rather than having its
 * result shifted.
 *
 * <p>The logic lives in an ordinary class because the body of an interface default method is
 * copied into the target interface, and keeping side and thread branching there is awkward and
 * risky.
 */
@Mixin(value = BlockGetter.class, priority = 1200)
public interface ClipNetMixin {

    @Inject(method = "clip", at = @At("HEAD"), cancellable = true)
    default void aero$shiftAimingRay(ClipContext context,
                                     CallbackInfoReturnable<BlockHitResult> cir) {
        // No exception from here may drop a foreign raycast: the net is an auxiliary layer, and
        // clip is called from places where a crash is fatal (physics, AI, rendering).
        try {
            BlockHitResult shifted = ClipNet.tryShift((BlockGetter) this, context);
            if (shifted != null) cir.setReturnValue(shifted);
        } catch (Throwable ignored) { }
    }
}
