package com.playsi.aero_cam_sync.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.utils.CameraController;
import com.playsi.aero_cam_sync.client.utils.FirstPersonCompat;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Совместимость с модом First-person Model (tr7zw).
 *
 * <p>Мод рендерит тело игрока от первого лица обычным {@link PlayerRenderer}, сдвигая
 * его за камеру. Наша камера наклоняется вокруг ног, а тело остаётся вертикальным, и
 * голова «отрывается». Здесь мы наклоняем тело тем же сглаженным тилтом вокруг ног
 * (тот же пивот, что у камеры), поэтому голова возвращается под камеру, а тело выглядит
 * стоящим на наклонной палубе.</p>
 *
 * <p>Срабатывает СТРОГО когда First-person Model реально рендерит тело от первого лица
 * ({@link FirstPersonCompat#isRenderingFirstPersonBody()}) — чужие игроки и вид от
 * третьего лица не затрагиваются. Без установленного мода миксин — no-op.</p>
 */
@Mixin(PlayerRenderer.class)
public abstract class FirstPersonModelTiltMixin {

    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void aero$tiltFirstPersonBody(AbstractClientPlayer entity, PoseStack poseStack,
                                          float bob, float yBodyRot, float partialTick, float scale,
                                          CallbackInfo ci) {
        if (!Config.MOD_ENABLED.get()) return;

        // Только во время рендера тела от первого лица модом First-person Model.
        if (!FirstPersonCompat.isRenderingFirstPersonBody()) return;

        // Наклоняем тело только когда камера СДВИГАЕТСЯ по позиции (MODIFY_CAMERA_POS):
        // именно сдвиг уводит камеру с глаз и отрывает голову. Тот же наклон вокруг ног
        // возвращает голову под камеру. Если сдвига нет (только поворот), камера остаётся
        // на глазах — тело должно остаться вертикальным, иначе голова, наоборот, оторвётся.
        if (!Config.MODIFY_CAMERA_POS.get()) return;

        if (!CameraController.shouldApplyTilt()) return;

        // Тот же эффективный тилт, что использует сдвиг камеры (с учётом ослабления у стены),
        // — поэтому голова отслеживает камеру кадр в кадр.
        Quaternionf tilt = CameraController.getSmoothedTilt();
        poseStack.mulPose(tilt);
    }
}
