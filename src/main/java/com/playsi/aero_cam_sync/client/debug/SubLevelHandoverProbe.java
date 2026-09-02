package com.playsi.aero_cam_sync.client.debug;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.client.sublevel.SubLevelThresholds;
import com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess;
import com.playsi.aero_cam_sync.client.tilt.DeckOrientation;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.neoforged.fml.loading.FMLLoader;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Dev probe GUARDING the sub-level choice: it watches the ACS answer, not Sable's raw one, so noise
 * means the choice has started depending on whoever Sable named first again.
 *
 * <p>It answers three questions at once, because each is useless alone: whether it flaps (a real
 * transition is ONE switch, so single changes are not an event), whether it is visible (the angle is
 * measured between tilt TARGETS, so two sub-levels lying alike give zero), and whether the activation
 * threshold is the cause (one passing and the other not gives the maximum amplitude however
 * parallel they are).
 *
 * <p>Reads only, dev runs only.
 */
public final class SubLevelHandoverProbe {

    private SubLevelHandoverProbe() {}

    /** Static and final, so the call is dead code in a release. */
    public static final boolean DEV = !FMLLoader.isProduction();

    private static final Vector3f WORLD_UP = new Vector3f(0f, 1f, 0f);

    /** Silence this long closes a run: it is then printed and the counters reset. */
    private static final long QUIET_MS = 1000L;

    /** Two switches is a legitimate step off and back at a sub-level edge. */
    private static final int RUN_MIN = 3;

    private static @Nullable UUID lastId = null;
    private static final Vector3f lastTarget = new Vector3f(WORLD_UP);
    private static boolean lastGatePassed = false;
    private static boolean seeded = false;

    private static long runStartedAt = 0L;
    private static long lastSwitchAt = 0L;
    private static int runSwitches = 0;
    private static float runMaxDegrees = 0f;
    private static boolean runGateDiffers = false;

    /** Called before the activation threshold: the choice does not depend on it. */
    public static void sample(@Nullable ClientSubLevel subLevel, float partialTick) {
        if (!DEV) return;

        long now = System.currentTimeMillis();

        UUID id = null;
        boolean gatePassed = false;
        Vector3f target = new Vector3f(WORLD_UP);

        if (subLevel != null) {
            // Inside Camera#setup: may not drop the frame, and a Sable failure reads as world up.
            try {
                id = subLevel.getUniqueId();
                gatePassed = SubLevelThresholds.passes(subLevel);
                if (gatePassed) {
                    Pose3dc pose = subLevel.renderPose(partialTick);
                    Vector3f up = pose == null ? null : DeckOrientation.targetUp(pose);
                    if (up != null) target.set(up);
                }
            } catch (Throwable ignored) {
                // world up stands
            }
        }

        if (!seeded) {
            seeded = true;
            remember(id, target, gatePassed);
            return;
        }

        if (!Objects.equals(id, lastId)) {
            if (runSwitches == 0) runStartedAt = now;
            runSwitches++;
            runMaxDegrees = Math.max(runMaxDegrees, angleDegrees(lastTarget, target));
            runGateDiffers |= gatePassed != lastGatePassed;
            lastSwitchAt = now;
        } else if (runSwitches > 0 && now - lastSwitchAt > QUIET_MS) {
            report();
            reset();
        }

        remember(id, target, gatePassed);
    }

    private static void remember(@Nullable UUID id, Vector3f target, boolean gatePassed) {
        lastId = id;
        lastTarget.set(target);
        lastGatePassed = gatePassed;
    }

    private static void report() {
        if (runSwitches < RUN_MIN) return;
        if (!ClientTiltAccess.isDebugMessages()) return;

        AeroCamSync.LOGGER.info(
                "[AeroCamSync] sub-level under the player flapped: {} switches in {} s,"
                        + " tilt target differed by up to {} deg, activation gate differed: {}",
                runSwitches,
                String.format("%.1f", (lastSwitchAt - runStartedAt) / 1000f),
                String.format("%.1f", runMaxDegrees),
                runGateDiffers ? "yes" : "no");
    }

    private static void reset() {
        runSwitches = 0;
        runMaxDegrees = 0f;
        runGateDiffers = false;
    }

    private static float angleDegrees(Vector3f a, Vector3f b) {
        float dot = Math.max(-1f, Math.min(1f, a.dot(b)));
        return (float) Math.toDegrees(Math.acos(dot));
    }
}
