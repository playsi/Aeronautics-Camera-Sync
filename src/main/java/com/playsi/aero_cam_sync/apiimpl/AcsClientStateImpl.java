package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.AcsClientState;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.tilt.CameraController;
import com.playsi.aero_cam_sync.client.camera.FrameVanillaState;
import com.playsi.aero_cam_sync.client.sublevel.SubLevelTracker;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * The client half of the snapshot.
 *
 * <p>A client class, despite living in a common package. It holds a {@link ClientSubLevel}
 * and must not load on a dedicated server. The single entry point {@link #capture()} returns the
 * INTERFACE, and {@code AcsStateImpl} calls it only inside the {@code isClientSide} branch, so on a
 * server the reference never executes and the class never loads. The same trick as in
 * {@code TiltAccess} and {@code SideGate}. The handshake crash already happened once (Issue
 * #33).
 */
final class AcsClientStateImpl implements AcsClientState {

    private final Vec3 vanillaCameraPos;
    private final Vec3 cameraPos;
    private final Quaternionf vanillaCameraRot;
    private final Quaternionf cameraRot;
    private final float tiltScale;
    private final boolean firstPerson;
    private final @Nullable String tiltSource;
    private final @Nullable ClientSubLevel tiltSubLevel;

    private AcsClientStateImpl(Vec3 vanillaCameraPos, Vec3 cameraPos,
                               Quaternionf vanillaCameraRot, Quaternionf cameraRot,
                               float tiltScale, boolean firstPerson,
                               @Nullable String tiltSource,
                               @Nullable ClientSubLevel tiltSubLevel) {
        this.vanillaCameraPos = vanillaCameraPos;
        this.cameraPos = cameraPos;
        this.vanillaCameraRot = vanillaCameraRot;
        this.cameraRot = cameraRot;
        this.tiltScale = tiltScale;
        this.firstPerson = firstPerson;
        this.tiltSource = tiltSource;
        this.tiltSubLevel = tiltSubLevel;
    }

    static AcsClientState capture() {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        Vec3 cameraPos = camera.getPosition();
        Quaternionf cameraRot = new Quaternionf(camera.rotation());

        // Until the camera has been through Camera#setup at all (early frames, the main menu)
        // there is simply no vanilla value: the current one is served, and it is the untilted one.
        boolean captured = FrameVanillaState.hasValue();
        Vec3 vanillaPos = captured ? FrameVanillaState.pos() : cameraPos;
        Quaternionf vanillaRot = captured ? FrameVanillaState.rot() : new Quaternionf(cameraRot);

        return new AcsClientStateImpl(
                vanillaPos,
                cameraPos,
                vanillaRot,
                cameraRot,
                CameraController.tiltScale(),
                mc.options.getCameraType().isFirstPerson(),
                // A PER-FRAME VALUE, not a registry, and there is no registry any more: the three
                // "who holds the switch" lists went with the switches. "Who did what this frame" is
                // answered by phase 2, AcsConditions.frameResolved.
                CameraController.tiltSource(),
                SubLevelTracker.getHeldSubLevel());
    }

    @Override public Vec3 vanillaCameraPos() { return vanillaCameraPos; }
    @Override public Vec3 cameraPos() { return cameraPos; }
    @Override public Quaternionf vanillaCameraRot() { return new Quaternionf(vanillaCameraRot); }
    @Override public Quaternionf cameraRot() { return new Quaternionf(cameraRot); }
    @Override public float tiltScale() { return tiltScale; }
    @Override public boolean firstPerson() { return firstPerson; }
    @Override public @Nullable String tiltSource() { return tiltSource; }
    @Override public @Nullable ClientSubLevel tiltSubLevel() { return tiltSubLevel; }
}
