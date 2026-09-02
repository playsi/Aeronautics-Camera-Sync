package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.AcsConditions;
import com.playsi.aero_cam_sync.api.ConditionContext;
import com.playsi.aero_cam_sync.api.FrameReport;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The {@link AcsConditions} subscriber registry and THIS frame's condition state.
 *
 * <p>Collection happens EXACTLY ONCE per frame, and that is not an optimisation: it makes the
 * outcome definite. Two mods that skip each other are both skipped and the frame goes to the
 * baseline, in finite time; any re-asking scheme would have to adjudicate between two FOREIGN
 * mods.
 *
 * <p>The reset is mandatory even with nobody to ask. Return from {@link #collect} before it
 * and the last frame on which somebody disabled collision disables it forever.
 */
public final class Conditions {

    private Conditions() {}

    private record Registered(String modId, AcsConditions conditions) {}

    /** Copy-on-write: registration is rare, reads happen every frame on the render thread. */
    private static volatile List<Registered> subscribers = List.of();

    /** {@code volatile}: written on the render thread, read from the client tick too. */
    private static volatile boolean baselineSkipped = false;
    private static volatile boolean thirdPerson = false;
    private static volatile boolean collisionTakenOver = false;

    private static volatile Set<String> skipped = Set.of();


    public static synchronized void add(String modId, AcsConditions conditions) {
        List<Registered> next = new ArrayList<>(subscribers);
        next.add(new Registered(modId, conditions));
        subscribers = List.copyOf(next);
    }

    public static boolean isEmpty() {
        return subscribers.isEmpty();
    }

    /**
     * Phase 1. Call as the FIRST line of the frame. A subscriber that throws does not drop the
     * frame, and whatever it stated beforehand stands: rolling that back would leave a half-applied
     * state with no explanation.
     */
    public static void collect(@Nullable Player player, float partialTick, boolean firstPerson) {
        List<Registered> list = subscribers;

        // No player means nobody to ask, but the reset is still mandatory.
        if (list.isEmpty() || player == null) {
            reset();
            return;
        }

        FrameConditionsImpl out = new FrameConditionsImpl();
        ConditionContext context = new ConditionContextImpl(player, partialTick, firstPerson);

        for (Registered r : list) {
            out.owner(r.modId());
            try {
                r.conditions().conditionsFor(context, out);
            } catch (Throwable t) {
                ApiLog.warn(r.modId(), "conditionsFor() threw; anything it had already stated"
                        + " stands for this frame: {}", String.valueOf(t));
            }
        }

        baselineSkipped = out.baselineSkipped;
        thirdPerson = out.thirdPerson;
        collisionTakenOver = out.collisionTakenOver;
        skipped = out.skipped == null ? Set.of() : Set.copyOf(out.skipped);
    }

    private static void reset() {
        baselineSkipped = false;
        thirdPerson = false;
        collisionTakenOver = false;
        // The check saves a volatile write on the vast majority of frames.
        if (!skipped.isEmpty()) skipped = Set.of();
    }

    /**
     * Phase 2: hand out the report. Call as the LAST line of the frame, after the pose is applied
     * and {@code wallScale} computed, or {@code tiltScale()} would be a frame stale.
     */
    public static void report(Player player, float partialTick, boolean firstPerson,
                              @Nullable String tiltSource, boolean baselineActive,
                              Quaternionf applied, Vec3 eyeOffset, float tiltScale) {
        List<Registered> list = subscribers;
        if (list.isEmpty()) return;

        Set<String> s = skipped;
        FrameReport report = new FrameReportImpl(player, partialTick, firstPerson, tiltSource,
                baselineActive, new Quaternionf(applied), eyeOffset, tiltScale,
                s.isEmpty() ? List.of() : List.copyOf(s));

        for (Registered r : list) {
            try {
                r.conditions().frameResolved(report);
            } catch (Throwable t) {
                ApiLog.warn(r.modId(), "frameResolved() threw; the frame is already applied"
                        + " and nothing is rolled back: {}", String.valueOf(t));
            }
        }
    }


    public static boolean baselineSkipped() {
        return baselineSkipped;
    }

    public static boolean thirdPersonAllowed() {
        return thirdPerson;
    }

    public static boolean cameraCollisionTakenOver() {
        return collisionTakenOver;
    }

    /** Asked for every candidate in {@code TiltSources.resolve}; an empty set costs one check. */
    public static boolean isSkipped(String modId) {
        Set<String> s = skipped;
        return !s.isEmpty() && s.contains(modId);
    }

    /** Order is not guaranteed, as the contract says. */
    public static List<String> skippedMods() {
        Set<String> s = skipped;
        if (s.isEmpty()) return Collections.emptyList();
        return List.copyOf(s);
    }
}
