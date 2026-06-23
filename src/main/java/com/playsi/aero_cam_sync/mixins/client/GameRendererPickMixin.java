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

        // Камера определяет перекрестие, поэтому весь pick делаем заново от позиции
        // камеры и вдоль её (наклонённого) направления взгляда. getViewVector у
        // LocalPlayer уже наклонён через EntityLookMixin, когда включён поворот камеры.
        Vec3 eyes = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 look = player.getViewVector(partialTick);

        double blockReach = player.blockInteractionRange();
        double entityReach = player.entityInteractionRange();

        // --- Блоки: клип от камеры на дальность взаимодействия с блоками ---
        Vec3 blockEnd = eyes.add(look.scale(blockReach));
        BlockHitResult blockHit;
        LevelClipMixinState.inTiltedClip = true;
        try {
            blockHit = mc.level.clip(new ClipContext(
                    eyes, blockEnd,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
        } finally {
            LevelClipMixinState.inTiltedClip = false;
        }

        boolean hasBlock = blockHit.getType() != HitResult.Type.MISS;
        double blockDistSqr = hasBlock
                ? eyes.distanceToSqr(blockHit.getLocation())
                : Double.MAX_VALUE;

        if (Config.DEBUG_PICK_RAYS.get()) {
            Vec3 vanillaEyes = player.getEyePosition(partialTick);
            DebugRayRenderer.submitPickRay(vanillaEyes, vanillaEyes.add(look.scale(blockReach)), 0.5f, 0.5f, 0.5f);
            DebugRayRenderer.submitPickRay(eyes, blockEnd, 1.0f, 1.0f, 0.0f);
        }

        // --- Сущности: ищем тоже от камеры, но НЕ дальше попавшего блока, иначе
        //     можно было бы «прокликать» моба сквозь стену (Issue: mob through blocks). ---
        double entitySearch = hasBlock
                ? Math.min(entityReach, Math.sqrt(blockDistSqr))
                : entityReach;
        Vec3 entityEnd = eyes.add(look.scale(entitySearch));
        AABB searchBox = new AABB(eyes, entityEnd).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eyes, entityEnd, searchBox,
                e -> !e.isSpectator() && e.isPickable(),
                entitySearch * entitySearch
        );

        // Сущность побеждает только если она перед блоком (и в радиусе досягаемости).
        HitResult result;
        if (entityHit != null
                && eyes.distanceToSqr(entityHit.getLocation()) <= blockDistSqr) {
            result = entityHit;
        } else {
            result = blockHit; // может быть MISS — это корректно: перекрестие смотрит в пустоту
        }

        mc.hitResult = result;

        if (Config.DEBUG_PICK_RAYS.get() && result.getType() != HitResult.Type.MISS) {
            Vec3 h = result.getLocation();
            float s = 0.05f;
            DebugRayRenderer.submitPickRay(h.add(-s, 0, 0), h.add(s, 0, 0), 0f, 1f, 0f);
            DebugRayRenderer.submitPickRay(h.add(0, -s, 0), h.add(0, s, 0), 0f, 1f, 0f);
            DebugRayRenderer.submitPickRay(h.add(0, 0, -s), h.add(0, 0, s), 0f, 1f, 0f);
        }
    }
}
