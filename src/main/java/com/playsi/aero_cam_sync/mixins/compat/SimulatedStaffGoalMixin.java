package com.playsi.aero_cam_sync.mixins.compat;

import com.llamalad7.mixinextras.sugar.Local;
import com.playsi.aero_cam_sync.TiltAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The physics staff's other half: the point it drags an object TO. The client sends an ALREADY
 * tilted {@code playerRelativeGoal}, but the server adds it to a hand-assembled eye that knows
 * nothing of the tilt, so the object hangs where you are not aiming.
 *
 * <p>{@code @Pseudo} plus {@code targets}: {@code DragSession} is a private nested class that a
 * plain {@code @Mixin(Class)} cannot reference.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler$DragSession",
        remap = false)
public abstract class SimulatedStaffGoalMixin {

    @ModifyArgs(
            method = "physicsTick",
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;add(DDD)Lorg/joml/Vector3d;", remap = false),
            require = 0
    )
    private void aero$tiltDragGoal(Args args, @Local Player player) {
        Vec3 offset = TiltAccess.aimEyeOffset(player);
        if (offset == null) return;

        args.set(0, (double) args.get(0) + offset.x);
        args.set(1, (double) args.get(1) + offset.y);
        args.set(2, (double) args.get(2) + offset.z);
    }
}
