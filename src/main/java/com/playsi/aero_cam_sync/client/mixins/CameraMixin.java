package com.playsi.aero_cam_sync.client.mixins;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.client.Config;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.playsi.aero_cam_sync.client.utils.CameraUtils.*;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void applyTerrainTilt(
            BlockGetter level, Entity entity,
            boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {

        if (!Config.MOD_ENABLED.get()) return;
        if (!shouldApplyTilt()) return;

        Minecraft mc = Minecraft.getInstance();
        float deltaTime = mc.getTimer().getRealtimeDeltaTicks();
        boolean onGround = mc.player.onGround();

        ClientSubLevel subLevel = getClientSubLevel(mc.player);

        if (subLevel != null) {
            AeroCamSync.LOGGER.info(subLevel.toString());
        }

        // Нет сабвела — игрок на обычном мире, плавно возвращаемся к identity
        if (subLevel == null) {
            updateSmoothedTilt(null, deltaTime, false);
            applyTiltToCamera((Camera) (Object) this);
            return;
        }

        // Игрок в воздухе над сабвелом — замораживаем тилт (не рейкастим)
//        if (!onGround) {
//            updateSmoothedTilt(null, deltaTime, true); // freeze = true
//            applyTiltToCamera((Camera) (Object) this);
//            return;
//        }

        // Игрок на сабвеле и на земле — считаем нормаль
        Pose3dc pose = subLevel.renderPose(partialTick);
        Vector3f surfaceNormal = getSurfaceNormal(subLevel, pose);

        // surfaceNormal == null если все лучи промахнулись — плавно к identity
        updateSmoothedTilt(surfaceNormal, deltaTime, false);
        applyTiltToCamera((Camera) (Object) this);
    }

    //TODO задержка камеры
}