package com.playsi.aero_cam_sync.mixins.client;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import com.playsi.aero_cam_sync.client.utils.CameraController;
import com.playsi.aero_cam_sync.client.utils.LevelClipMixinState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 1100)
public abstract class GameRendererPickMixin {

    @Shadow
    public abstract void render(DeltaTracker deltaTracker, boolean renderLevel);

    @Inject(method = "pick", at = @At("TAIL"))
    private void recalculateTiltedPick(float partialTick, CallbackInfo ci) {

        if (!Config.isLoaded()) return;
        if (!Config.MOD_ENABLED.get() || !CameraController.shouldApplyTilt()) return;
        if (!Config.MODIFY_CAMERA_POS.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        if (player == null || mc.level == null) return;

        HitResult currentResult = mc.hitResult;

        if (currentResult != null && currentResult.getType() == HitResult.Type.ENTITY) {
            return;
        }

        Vec3 eyes = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 look = player.getViewVector(partialTick);
        double reach = player.blockInteractionRange();
        Vec3 endPoint = eyes.add(look.scale(reach));

        if (Config.DEBUG_PICK_RAYS.get()) {
            Vec3 vanillaEyes = player.getEyePosition(partialTick);
            Vec3 vanillaEnd = vanillaEyes.add(look.scale(reach));
            DebugRayRenderer.submitPickRay(vanillaEyes, vanillaEnd, 0.5f, 0.5f, 0.5f);
            DebugRayRenderer.submitPickRay(eyes, endPoint, 1.0f, 1.0f, 0.0f);
        }

        AABB searchBox = new AABB(eyes, endPoint).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eyes, endPoint, searchBox,
                e -> !e.isSpectator() && e.isPickable(),
                reach * reach
        );

        if (entityHit != null) {
            mc.hitResult = entityHit;

            if (Config.DEBUG_PICK_RAYS.get()) {
                Vec3 h = entityHit.getLocation();
                float s = 0.05f;
                DebugRayRenderer.submitPickRay(h.add(-s, 0, 0), h.add(s, 0, 0), 0f, 1f, 0f);
                DebugRayRenderer.submitPickRay(h.add(0, -s, 0), h.add(0, s, 0), 0f, 1f, 0f);
                DebugRayRenderer.submitPickRay(h.add(0, 0, -s), h.add(0, 0, s), 0f, 1f, 0f);
            }
            return;
        }

        BlockHitResult blockHit;
        LevelClipMixinState.inTiltedClip = true;
        try {
            blockHit = mc.level.clip(new ClipContext(
                    eyes, endPoint,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
        } finally {
            LevelClipMixinState.inTiltedClip = false;
        }

        if (blockHit.getType() == HitResult.Type.MISS) return;

        if (currentResult != null && currentResult.getType() == HitResult.Type.BLOCK) {
            double currentDist = currentResult.getLocation().distanceToSqr(eyes.x, eyes.y, eyes.z);
            double tiltedDist = blockHit.getLocation().distanceToSqr(eyes.x, eyes.y, eyes.z);
            if (currentDist <= tiltedDist) return;
        }

        mc.hitResult = blockHit;

        if (Config.DEBUG_PICK_RAYS.get()) {
            Vec3 h = blockHit.getLocation();
            float s = 0.05f;
            DebugRayRenderer.submitPickRay(h.add(-s, 0, 0), h.add(s, 0, 0), 0f, 1f, 0f);
            DebugRayRenderer.submitPickRay(h.add(0, -s, 0), h.add(0, s, 0), 0f, 1f, 0f);
            DebugRayRenderer.submitPickRay(h.add(0, 0, -s), h.add(0, 0, s), 0f, 1f, 0f);
        }
    }
}