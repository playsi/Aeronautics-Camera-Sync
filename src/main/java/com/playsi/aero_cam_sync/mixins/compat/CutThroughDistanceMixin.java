package com.playsi.aero_cam_sync.mixins.compat;

import com.bawnorton.mixinsquared.TargetHandler;
import com.playsi.aero_cam_sync.client.compat.CutThroughCompat;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Cut Through compatibility: puts its "which is nearer" choice on a sub-level-aware metric. On a
 * sub-level it compares a plot-space hit against a world-space eye, so both distances are garbage of
 * order 20 million squared and which wins is chance. The bug reproduces without ACS.
 *
 * <p>The ray ORIGIN is handled by {@link com.playsi.aero_cam_sync.ClipNet}; the METRIC is this
 * mixin, where the net cannot reach because there is no ray, only arithmetic over finished
 * points.
 *
 * Why MixinSquared rather than a plain selector
 *
 * <p>Cut Through's handlers use {@code @Local(ref = true)}, so MixinExtras inserts them during
 * injector application, not mixin merge, and a plain injector cannot see them AT ANY PRIORITY. Nor
 * can they be named: their target-class names carry a prefix that depends on the mod set, and Mixin
 * selectors have no name patterns.
 *
 * <p>{@code priority = 1500} is mandatory, because the library's extension has to run after ordinary
 * injectors. {@code require = 0, expect = 0} are explicit because the json sets
 * {@code defaultRequireValue = 1} while a missing Cut Through is normal; the price is a silent
 * break on a rename, which {@code CutThroughCompat} watches for.
 *
 * <p>{@code require = 0} does NOT protect against MixinSquared itself being absent. Without
 * the library the {@code @MixinSquared:Handler} selector is unregistered, parsing the string throws
 * {@code InvalidInjectionException} and the mod does not start at all. {@code require} is about
 * "no targets found"; here the string itself is invalid. A repack or a modpack builder can strip
 * the library, so the gate stays: no library, no mixin, one explanatory log line, and the mod
 * lives.
 */
@Mixin(value = GameRenderer.class, priority = 1500)
public abstract class CutThroughDistanceMixin {

    /** {@code pick$0}: its own COLLIDER hit against the vanilla one, both on the honest metric. */
    @TargetHandler(mixin = "fuzs.cutthrough.mixin.client.GameRendererMixin", name = "pick$0")
    @Redirect(
            method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"),
            require = 0,
            expect = 0
    )
    private double aero$sableDistanceOccluder(Vec3 self, Vec3 other) {
        return aero$distance(self, other);
    }

    /** {@code pick$1}: the {@code LocalRef} hit against the entity one, same comparison. */
    @TargetHandler(mixin = "fuzs.cutthrough.mixin.client.GameRendererMixin", name = "pick$1")
    @Redirect(
            method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"),
            require = 0,
            expect = 0
    )
    private double aero$sableDistanceEntity(Vec3 self, Vec3 other) {
        return aero$distance(self, other);
    }

    /**
     * The reference point in {@code pick$0}: Sable's funnel, not the vanilla eye.
     *
     * <p>Both compared rays already leave the funnel (the vanilla one via Sable, the Cut Through
     * one via {@link com.playsi.aero_cam_sync.ClipNet}), and inside a pick the funnel returns the
     * eye with the tilt correction applied. Measuring from the vanilla eye would compare distances
     * from a point nothing was fired from: at a ~35 degree tilt the correction is about half a
     * block, enough to flip a borderline choice.
     */
    @TargetHandler(mixin = "fuzs.cutthrough.mixin.client.GameRendererMixin", name = "pick$0")
    @Redirect(
            method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"),
            require = 0,
            expect = 0
    )
    private Vec3 aero$sableEyeOccluder(Entity entity, float partialTick) {
        return aero$eye(entity, partialTick);
    }

    @TargetHandler(mixin = "fuzs.cutthrough.mixin.client.GameRendererMixin", name = "pick$1")
    @Redirect(
            method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"),
            require = 0,
            expect = 0
    )
    private Vec3 aero$sableEyeEntity(Entity entity, float partialTick) {
        return aero$eye(entity, partialTick);
    }

    private static double aero$distance(Vec3 self, Vec3 other) {
        // The only outward sign that the link to the foreign handlers is alive: a broken
        // @TargetHandler is silent under require = 0, and this line runs only if the redirect
        // really landed in pick$0/pick$1.
        CutThroughCompat.markWired();

        Level level = Minecraft.getInstance().level;
        if (level == null) return self.distanceToSqr(other);

        // The six-double overload and not the two-Vec3 one: Vec3 implements
        // Position, so an object call lands in distanceSquaredWithSubLevels(Level,Position,Position),
        // and in DefaultSableCompanion (the stub used when Sable is inactive) that one has a typo:
        // it computes d1/d2 as b.y()-b.y() and b.z()-b.z(), returning dx squared alone.
        return Sable.HELPER.distanceSquaredWithSubLevels(level,
                self.x, self.y, self.z, other.x, other.y, other.z);
    }

    private static Vec3 aero$eye(Entity entity, float partialTick) {
        return Sable.HELPER.getEyePositionInterpolated(entity, partialTick);
    }
}
