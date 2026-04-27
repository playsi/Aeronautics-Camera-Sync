package com.playsi.aero_cam_sync.client.mixins;

import com.playsi.aero_cam_sync.client.Config;
import com.playsi.aero_cam_sync.client.CameraUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererPickMixin {

    @Shadow @Final
    private Minecraft minecraft;

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void aero$overridePick(float partialTick, CallbackInfo ci) {
        if (!Config.MOD_ENABLED.get() || !CameraUtils.shouldApplyTilt()) return;

        Entity entity = minecraft.getCameraEntity();
        if (entity == null || minecraft.level == null || minecraft.player == null) return;

        ci.cancel();

        // ── Наклонённый вектор взгляда ──────────────────────────────────────────
        Vec3 vanilla = entity.getViewVector(partialTick);
        Vector3f v = new Vector3f((float) vanilla.x, (float) vanilla.y, (float) vanilla.z);
        CameraUtils.getSmoothedTilt().transform(v);
        Vec3 tiltedLook = new Vec3(v.x, v.y, v.z).normalize();

        Vec3 eye = entity.getEyePosition(partialTick);
        double blockRange  = minecraft.player.blockInteractionRange();
        double entityRange = minecraft.player.entityInteractionRange();
        Vec3 blockEnd = eye.add(tiltedLook.scale(blockRange));

        // ── 1. Блоки ────────────────────────────────────────────────────────────
        // NONE: ванильное поведение — можно кликать сквозь жидкость
        HitResult blockHit = entity.level().clip(new ClipContext(
                eye, blockEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));

        // SOURCE_ONLY: нужно только если в руке пустое ведро
        // (полная логика ведра — отдельный миксин, здесь минимально)
        HitResult fluidHit = null;
        if (isHoldingEmptyBucket()) {
            fluidHit = entity.level().clip(new ClipContext(
                    eye, blockEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, entity));
        }

        double blockDistSq = blockHit.getLocation().distanceToSqr(eye);

        HitResult primaryHit;
        double primaryDistSq;
        if (fluidHit != null && fluidHit.getType() != HitResult.Type.MISS) {
            double fluidDistSq = fluidHit.getLocation().distanceToSqr(eye);
            if (fluidDistSq <= blockDistSq) {
                primaryHit    = fluidHit;
                primaryDistSq = fluidDistSq;
            } else {
                primaryHit    = blockHit;
                primaryDistSq = blockDistSq;
            }
        } else {
            primaryHit    = blockHit;
            primaryDistSq = blockDistSq;
        }

        // ── 2. Сущности ─────────────────────────────────────────────────────────
        // Используем playerEye (main-world координаты), а не entity.getEyePosition(),
        // которое на sub-level контрапциях возвращает астрономические координаты.
        // Именно это заставляло sable расширять AABB до миллионов блоков.
        Vec3 playerEye = minecraft.player.getEyePosition(partialTick);

        double entitySearchRange = entityRange;
        if (primaryHit.getType() != HitResult.Type.MISS) {
            double hitDist = Math.sqrt(primaryDistSq);
            if (hitDist < entitySearchRange) entitySearchRange = hitDist;
        }

        Vec3   entityEnd     = playerEye.add(tiltedLook.scale(entitySearchRange));
        double entityRangeSq = entitySearchRange * entitySearchRange;
        // AABB строим от playerEye до entityEnd — всегда маленький, всегда в main world
        AABB searchBox = new AABB(playerEye, entityEnd).inflate(1.0);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                minecraft.player, playerEye, entityEnd, searchBox,
                e -> !e.isSpectator() && e.isPickable(),
                entityRangeSq);

        // ── 3. Финальный результат ───────────────────────────────────────────────
        if (entityHit != null) {
            double entityDistSq = entityHit.getLocation().distanceToSqr(playerEye);
            minecraft.hitResult = (entityDistSq < primaryDistSq) ? entityHit : primaryHit;
            minecraft.crosshairPickEntity = entityHit.getEntity();
        } else {
            minecraft.hitResult = primaryHit;
            minecraft.crosshairPickEntity = null;
        }
    }

    /** Только пустое ведро заставляет ванильный pick учитывать SOURCE_ONLY. */
    private boolean isHoldingEmptyBucket() {
        if (minecraft.player == null) return false;
        return minecraft.player.getMainHandItem().is(Items.BUCKET)
                || minecraft.player.getOffhandItem().is(Items.BUCKET);
    }

    //TODO фикс логики жидкостей
}