package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.TiltAccess;
import com.playsi.aero_cam_sync.api.AcsClientState;
import com.playsi.aero_cam_sync.api.AcsRay;
import com.playsi.aero_cam_sync.api.AcsState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A snapshot of the ACS state. A pure read: not one clip, not one write to shared state. A
 * mod calls this every frame, and a snapshot that changes anything gives bugs indistinguishable
 * from a genuine ACS one.
 *
 * <p>The client half is built by a factory declared to return the INTERFACE, so a server never
 * loads the class holding a {@code ClientSubLevel} field.
 */
final class AcsStateImpl implements AcsState {

    /** Whose snapshot this is, for the call summary. {@code null} means ACS took it, for events. */
    private final @Nullable AcsHandleImpl handle;
    private final Player player;
    private final float partialTick;

    private final boolean modEnabled;
    private final Vec3 vanillaEye;
    private final Vec3 aimEye;

    /** The foreign source's term, already inside {@link #aimEye}; exposed for diagnostics. */
    private final Vec3 sourceEyeOffset;

    /** The rotation applied to the look DIRECTION, or {@code null} if it is vanilla. */
    private final @Nullable Quaternionf aimRotation;

    private final @Nullable Quaternionf posTilt;
    private final @Nullable Quaternionf lookTilt;
    private final boolean tiltApplied;
    private final boolean suppressed;
    private final List<String> suppressedBy;
    private final @Nullable AcsClientState client;

    static AcsState capture(@Nullable AcsHandleImpl handle, Player player, float partialTick) {
        boolean clientSide = player.level().isClientSide;
        return new AcsStateImpl(handle, player, partialTick, clientSide);
    }

    private AcsStateImpl(@Nullable AcsHandleImpl handle, Player player, float partialTick,
                         boolean clientSide) {
        this.handle = handle;
        this.player = player;
        this.partialTick = partialTick;

        this.modEnabled = clientSide
                ? com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.isModEnabled()
                : TiltAccess.getLookTilt(player) != null || TiltAccess.getPosTilt(player) != null;

        this.posTilt = TiltAccess.getPosTilt(player);
        this.lookTilt = TiltAccess.getLookTilt(player);

        this.vanillaEye = clientSide ? player.getEyePosition(partialTick) : player.getEyePosition();

        Vec3 offset = eyeOffset(player, partialTick, clientSide);
        this.aimEye = offset == null ? vanillaEye : vanillaEye.add(offset);
        this.aimRotation = lookTilt;

        // Separate from aimEye although included in it: subtraction cannot answer "how much of
        // this is not yours", since the other term depends on ACS smoothing and on wallScale.
        this.sourceEyeOffset = offset == null
                ? Vec3.ZERO
                : TiltAccess.sourceEyeOffset(player);

        this.tiltApplied = isTiltApplied();

        this.suppressed = SuppressionLeases.isSuppressed();
        this.suppressedBy = SuppressionLeases.holders();

        this.client = clientSide ? AcsClientStateImpl.capture() : null;
    }

    /**
     * Behind the same gates as the funnel and the net: three sources of one correction must switch
     * together. Intended side effect: a snapshot inside {@code withVanillaEye} returns the vanilla
     * eye.
     */
    private static @Nullable Vec3 eyeOffset(Player player, float partialTick, boolean clientSide) {
        if (!clientSide) return TiltAccess.aimEyeOffset(player);
        if (!com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.isAimShiftAllowed()) return null;
        return com.playsi.aero_cam_sync.client.tilt.CameraController.aimEyeOffset(partialTick);
    }

    /**
     * Either half counts, measured as a displacement length against the net's threshold. Computed
     * from FACT, not settings: during the ease-out after {@code suppress()} this honestly says true.
     */
    private boolean isTiltApplied() {
        if (aimEye.distanceToSqr(vanillaEye) >= TiltAccess.EPSILON_SQR) return true;
        if (aimRotation == null) return false;
        return aimLook(partialTick).distanceToSqr(vanillaLook(partialTick)) >= TiltAccess.EPSILON_SQR;
    }

    // ------------------------------------------------------------------ AcsState

    @Override public boolean modEnabled() { return modEnabled; }
    @Override public boolean tiltApplied() { return tiltApplied; }
    @Override public boolean suppressed() { return suppressed; }
    @Override public List<String> suppressedBy() { return suppressedBy; }

    @Override public @Nullable Quaternionf posTilt() {
        return posTilt == null ? null : new Quaternionf(posTilt);
    }

    @Override public @Nullable Quaternionf lookTilt() {
        return lookTilt == null ? null : new Quaternionf(lookTilt);
    }

    @Override public Vec3 eyeOffset() { return sourceEyeOffset; }
    @Override public Vec3 vanillaEye() { return vanillaEye; }
    @Override public Vec3 aimEye() { return aimEye; }

    @Override public Vec3 vanillaLook(float partialTick) {
        // From raw xRot/yRot: a mod may ask for an arbitrary partialTick, and no value is stored.
        return player.calculateViewVector(
                player.getViewXRot(partialTick), player.getViewYRot(partialTick));
    }

    @Override public Vec3 aimLook(float partialTick) {
        Vec3 vanilla = vanillaLook(partialTick);
        if (aimRotation == null) return vanilla;
        Vector3f v = new Vector3f((float) vanilla.x, (float) vanilla.y, (float) vanilla.z);
        aimRotation.transform(v);
        return new Vec3(v.x, v.y, v.z);
    }

    @Override public AcsRay vanillaRay(double reach) {
        return AcsRayImpl.of(vanillaEye, vanillaLook(partialTick), reach);
    }

    @Override public AcsRay aimRay(double reach) {
        if (handle != null) ApiLog.count(handle, AcsHandleImpl.Call.AIM_RAY);
        return AcsRayImpl.of(aimEye, aimLook(partialTick), reach);
    }

    @Override public @Nullable AcsClientState client() { return client; }
}
