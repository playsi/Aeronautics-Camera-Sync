package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.TiltContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * One frame's input for tilt sources.
 *
 * <p>Built once per frame in {@link TiltSources#resolve} and outliving exactly that poll. The
 * getters return copies, so a source cannot corrupt shared state by accidentally calling one of
 * joml's mutating methods on a quaternion it was handed (almost all of them mutate).
 *
 * <p>Not one client type. A mod registers {@code TiltSource} through the common
 * {@code AcsHandle}, so it and everything in its signatures loads on a dedicated server. Hence
 * there is no sub-level here, and will not be: whoever needs one already works with Sable directly,
 * and the handshake crash would land on ACS (Issue #33).
 *
 * <p>For the same reason {@link #cameraPosFor} computes the camera position HERE, from the given
 * {@code vanillaCameraPos} and {@code feet}, rather than reaching into the client-side
 * {@code FrameVanillaState}. The formula does not fork: {@code CameraController} applies exactly
 * the same one to the camera, and there is nowhere for them to drift, given the same two numbers of
 * the frame as input.
 */
record TiltContextImpl(Player player,
                       float partialTick,
                       float deltaTicks,
                       @Nullable Vector3f normal,
                       Quaternionf acs,
                       boolean firstPerson,
                       Vec3 vanillaCameraPos,
                       Vec3 feet) implements TiltContext {

    @Override
    public @Nullable Vector3f surfaceNormal() {
        return normal == null ? null : new Vector3f(normal);
    }

    @Override
    public Quaternionf acsTilt() {
        return new Quaternionf(acs);
    }

    @Override
    public Vec3 cameraPosFor(Quaternionf tilt) {
        Objects.requireNonNull(tilt, "tilt");

        // A COPY is normalised: a foreign quaternion is never corrupted, and an unnormalised one
        // would give a point displaced from the player by the quaternion's length, whose cause the
        // modder would come looking for here.
        Vector3f rel = new Vector3f(
                (float) (vanillaCameraPos.x - feet.x),
                (float) (vanillaCameraPos.y - feet.y),
                (float) (vanillaCameraPos.z - feet.z));
        new Quaternionf(tilt).normalize().transform(rel);

        return feet.add(rel.x, rel.y, rel.z);
    }
}
