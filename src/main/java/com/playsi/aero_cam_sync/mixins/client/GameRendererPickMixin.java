package com.playsi.aero_cam_sync.mixins.client;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import com.playsi.aero_cam_sync.client.utils.CameraController;
import com.playsi.aero_cam_sync.client.utils.LevelClipMixinState;
import com.playsi.aero_cam_sync.client.utils.ModCompat;
import dev.ryanhcode.sable.Sable;
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
        // ВАЖНО: Sable-clip для хита по саблевелу возвращает точку в ЛОКАЛЬНЫХ (plot)
        // координатах палубы, а не в мировых. Ванильный distanceToSqr от мировой позиции
        // камеры до такой точки даёт мусор (расстояние до далёкого plot-региона) → кламп
        // «сущность не дальше блока» ломается и моб выбирается сквозь блок на палубе.
        // distanceSquaredWithSubLevels проецирует обе точки в мир и меряет честно.
        double blockDistSqr = hasBlock
                ? Sable.HELPER.distanceSquaredWithSubLevels(mc.level, eyes, blockHit.getLocation())
                : Double.MAX_VALUE;

        if (Config.DEBUG_PICK_RAYS.get()) {
            Vec3 vanillaEyes = player.getEyePosition(partialTick);
            DebugRayRenderer.submitPickRay(vanillaEyes, vanillaEyes.add(look.scale(blockReach)), 0.5f, 0.5f, 0.5f);
            DebugRayRenderer.submitPickRay(eyes, blockEnd, 1.0f, 1.0f, 0.0f);
        }

        // --- Окклюзия сущностей ОТДЕЛЕНА от прицельного блок-хита ---------------------
        // OUTLINE = во что целишься (у травы outline есть), COLLIDER = что реально
        // преграждает удар (у травы коллизии нет). Раньше мы использовали OUTLINE для
        // обеих ролей, поэтому трава считалась стеной и ломала Cut Through, который бьёт
        // сквозь такие блоки (он сам делает ровно этот COLLIDER-клип внутри pick).
        // С Cut Through берём COLLIDER: блоки палубы коллизию имеют → сквозь них по-прежнему
        // не пробить (Issue #26), а трава больше не отсекает моба за ней.
        // Без Cut Through остаёмся на OUTLINE, чтобы не привносить чужую механику.
        double occludeDistSqr = blockDistSqr;
        if (ModCompat.cutThroughLoaded()) {
            LevelClipMixinState.inTiltedClip = true;
            BlockHitResult colliderHit;
            try {
                colliderHit = mc.level.clip(new ClipContext(
                        eyes, blockEnd,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        player
                ));
            } finally {
                LevelClipMixinState.inTiltedClip = false;
            }
            occludeDistSqr = colliderHit.getType() != HitResult.Type.MISS
                    ? Sable.HELPER.distanceSquaredWithSubLevels(mc.level, eyes, colliderHit.getLocation())
                    : Double.MAX_VALUE;
        }
        boolean hasOccluder = occludeDistSqr != Double.MAX_VALUE;

        // --- Сущности: ищем тоже от камеры, но НЕ дальше преграждающего блока, иначе
        //     можно было бы «прокликать» моба сквозь стену (Issue: mob through blocks). ---
        double entitySearch = hasOccluder
                ? Math.min(entityReach, Math.sqrt(occludeDistSqr))
                : entityReach;
        Vec3 entityEnd = eyes.add(look.scale(entitySearch));
        AABB searchBox = new AABB(eyes, entityEnd).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eyes, entityEnd, searchBox,
                e -> !e.isSpectator() && e.isPickable(),
                entitySearch * entitySearch
        );

        // Сущность побеждает, только если она перед ПРЕГРАДОЙ (а не перед любым блоком с
        // outline) и в радиусе досягаемости. Дистанцию меряем саблевел-осведомлённо: и
        // сущность, и блок могут жить в plot-пространстве палубы (см. §1 подводных камней).
        // Если сущности нет — возвращаем прицельный OUTLINE-хит, поэтому по самой траве
        // по-прежнему можно попасть, когда за ней никого нет (поведение Cut Through).
        HitResult result;
        if (entityHit != null
                && Sable.HELPER.distanceSquaredWithSubLevels(mc.level, eyes, entityHit.getLocation()) <= occludeDistSqr) {
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
