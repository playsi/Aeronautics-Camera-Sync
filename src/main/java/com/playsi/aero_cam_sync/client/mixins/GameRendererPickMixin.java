package com.playsi.aero_cam_sync.client.mixins;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.playsi.aero_cam_sync.client.utils.CameraUtils.getSmoothedTilt;
import static com.playsi.aero_cam_sync.client.utils.CameraUtils.shouldApplyTilt;

@Mixin(GameRenderer.class)
public abstract class GameRendererPickMixin {

    @Shadow public abstract void render(DeltaTracker deltaTracker, boolean renderLevel);

    @Inject(method = "pick", at = @At("TAIL"))
    private void recalculateTiltedPick(float partialTick, CallbackInfo ci) {
        if (!Config.MOD_ENABLED.get() || !shouldApplyTilt()) return;
        if (!Config.MODIFY_CAMERA_ROT.get() && !Config.MODIFY_CAMERA_POS.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        Quaternionf tilt = getSmoothedTilt();
        if (player == null || mc.level == null) return;

        Vec3 eyes = mc.gameRenderer.getMainCamera().getPosition();

        Vec3 vanillaLook = player.getViewVector(partialTick);
        Vector3f look = new Vector3f(
                (float) vanillaLook.x,
                (float) vanillaLook.y,
                (float) vanillaLook.z
        );
        tilt.transform(look);
        Vec3 tiltedLook = new Vec3(look.x, look.y, look.z);

        double reach = mc.player.blockInteractionRange();
        Vec3 endPoint = eyes.add(tiltedLook.scale(reach));

        if (Config.DEBUG_PICK_RAYS.get()) {
            Vec3 vanillaEnd = eyes.add(vanillaLook.scale(reach));
            DebugRayRenderer.submitPickRay(eyes, vanillaEnd, 0.5f, 0.5f, 0.5f);
            DebugRayRenderer.submitPickRay(eyes, endPoint, 1.0f, 1.0f, 0.0f);
        }

        if (!Config.MODIFY_CAMERA_ROT.get() && Config.MODIFY_CAMERA_POS.get()) {
            endPoint = eyes.add(vanillaLook.scale(reach));
        }

        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                eyes, endPoint,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        AABB searchBox = player.getBoundingBox()
                .expandTowards(tiltedLook.scale(reach))
                .inflate(1.0);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eyes, endPoint, searchBox,
                e -> !e.isSpectator() && e.isPickable(),
                reach * reach
        );

        HitResult chosen;
        if (entityHit != null) {
            double blockDist = blockHit.getType() != HitResult.Type.MISS
                    ? Sable.HELPER.distanceSquaredWithSubLevels(mc.level, eyes, blockHit.getLocation())
                    : Double.MAX_VALUE;
            double entityDist = Sable.HELPER.distanceSquaredWithSubLevels(mc.level, eyes, entityHit.getLocation());
            chosen = entityDist < blockDist ? entityHit : blockHit;
        } else {
            chosen = blockHit;
        }

        if (Config.DEBUG_PICK_RAYS.get() && chosen.getType() != HitResult.Type.MISS) {
            Vec3 h = chosen.getLocation();
            float s = 0.05f;
            DebugRayRenderer.submitPickRay(h.add(-s,0,0), h.add(s,0,0), 0f,1f,0f);
            DebugRayRenderer.submitPickRay(h.add(0,-s,0), h.add(0,s,0), 0f,1f,0f);
            DebugRayRenderer.submitPickRay(h.add(0,0,-s), h.add(0,0,s), 0f,1f,0f);
        }

        mc.hitResult = chosen;
    }
}