package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.api.AcsState;
import com.playsi.aero_cam_sync.api.TiltListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The tilt listener registry.
 *
 * <p>{@link CopyOnWriteArrayList}: written once per load, read from frame events, exactly the
 * profile it was made for.
 *
 * <p>There is not one Minecraft type here (suppression events deal in strings), so
 * {@link SuppressionLeases} can call this class directly without breaking its unit test.
 */
public final class TiltListeners {

    private TiltListeners() {}

    private record Registered(String modId, TiltListener listener) {}

    private static final CopyOnWriteArrayList<Registered> LISTENERS = new CopyOnWriteArrayList<>();

    public static void add(String modId, TiltListener listener) {
        LISTENERS.add(new Registered(modId, listener));
    }

    /** Nobody subscribed: the event need not even be built (the snapshot costs an allocation). */
    public static boolean isEmpty() {
        return LISTENERS.isEmpty();
    }

    public static void tiltStarted(AcsState state) {
        for (Registered r : LISTENERS) {
            guard(r, () -> r.listener().onTiltStart(state));
        }
    }

    public static void tiltStopped(AcsState state) {
        for (Registered r : LISTENERS) {
            guard(r, () -> r.listener().onTiltStop(state));
        }
    }

    public static void suppressionChanged(boolean suppressed, List<String> by) {
        for (Registered r : LISTENERS) {
            guard(r, () -> r.listener().onSuppressionChanged(suppressed, by));
        }
    }

    /**
     * An exception in a foreign listener must not drop the frame: events leave from
     * {@code Camera#setup} and from the client tick, where a crash is fatal.
     */
    private static void guard(Registered r, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            AeroCamSync.LOGGER.error("[AeroCamSync] * {}: tilt listener threw", r.modId(), t);
        }
    }
}
