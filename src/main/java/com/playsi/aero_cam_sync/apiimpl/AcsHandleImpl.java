package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.api.AcsConditions;
import com.playsi.aero_cam_sync.api.AcsHandle;
import com.playsi.aero_cam_sync.api.AcsState;
import com.playsi.aero_cam_sync.api.AimPolicy;
import com.playsi.aero_cam_sync.api.TiltListener;
import com.playsi.aero_cam_sync.api.TiltSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/** One mod's handle. Created exactly once per {@code modId}; see {@link HandleRegistry}. */
final class AcsHandleImpl implements AcsHandle {

    /** What the summary counts. The calls themselves are not logged: this is a hot path. */
    enum Call { STATE, AIM_RAY, VANILLA_EYE }

    private final String modId;

    /** Not atomic: statistics, not state (see {@link ApiLog#count}). */
    final long[] counters = new long[Call.values().length];

    AcsHandleImpl(String modId) {
        this.modId = modId;
        ApiLog.event(modId, "api handle acquired");
    }

    @Override public String modId() { return modId; }

    @Override
    public AcsState state(Player player, float partialTick) {
        Objects.requireNonNull(player, "player");
        ApiLog.count(this, Call.STATE);
        return AcsStateImpl.capture(this, player, partialTick);
    }

    @Override
    public void suppress(long millis) {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            ApiLog.warn(modId, "suppress() does nothing on a dedicated server: the tilt is computed client-side");
            return;
        }
        if (millis <= 0) return;

        if (millis > SuppressionLeases.LONG_LEASE_MILLIS) {
            // Nothing to forbid, since "my camera mode does not get along with the tilt" is a
            // legitimate scenario, but it must be visible in the log.
            ApiLog.warn(modId, "suppression lease of {} ms is longer than {} ms", millis,
                    SuppressionLeases.LONG_LEASE_MILLIS);
        }
        ApiLog.event(modId, "tilt suppressed for {} ms", millis);
        SuppressionLeases.suppress(modId, millis);
    }

    @Override
    public void release() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) return;
        ApiLog.event(modId, "suppression released");
        SuppressionLeases.release(modId);
    }

    @Override public boolean isSuppressed() { return SuppressionLeases.isSuppressed(); }
    @Override public boolean isSuppressedByMe() { return SuppressionLeases.isSuppressedBy(modId); }

    @Override
    public void withVanillaEye(Runnable body) {
        Objects.requireNonNull(body, "body");
        withVanillaEye(() -> { body.run(); return null; });
    }

    @Override
    public <T> T withVanillaEye(Supplier<T> body) {
        Objects.requireNonNull(body, "body");
        ApiLog.count(this, Call.VANILLA_EYE);

        // The counter inside RenderEyeScope is static rather than ThreadLocal, and stays that way:
        // aiming lives on the render thread and background rays are kept out of it (Issue
        // #24). Opening the window from another thread would switch off the tilt on the main one.
        if (!isClientMainThread()) {
            ApiLog.warn(modId, "withVanillaEye() called off the client main thread"
                    + ", the body runs but without the scope");
            return body.get();
        }

        com.playsi.aero_cam_sync.client.aim.RenderEyeScope.enter();
        try {
            return body.get();
        } finally {
            com.playsi.aero_cam_sync.client.aim.RenderEyeScope.exit();
        }
    }

    private static boolean isClientMainThread() {
        if (FMLEnvironment.dist != Dist.CLIENT) return false;
        return com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.isRenderThread();
    }

    @Override
    public void addListener(TiltListener listener) {
        Objects.requireNonNull(listener, "listener");
        ApiLog.event(modId, "tilt listener registered");
        TiltListeners.add(modId, listener);
    }

    @Override
    public void addPolicy(AimPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        ApiLog.event(modId, "aim policy registered");
        AimPolicies.add(modId, policy);
    }

    @Override
    public void addTiltSource(int priority, TiltSource source) {
        Objects.requireNonNull(source, "source");
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            ApiLog.warn(modId, "addTiltSource() does nothing on a dedicated server"
                    + ": the tilt is computed client-side");
            return;
        }
        // The priority goes in the log line because "why does my source never fire" almost
        // always means "somebody registered higher", and that answer should be visible before the
        // modder comes asking.
        //
        // It is baked into the FORMAT rather than passed as an argument, against ApiLog's usual
        // rule: the dedup key is the format without values, so a mod registering two sources at
        // different priorities would see exactly one line and silently lose half its configuration.
        // The log cannot drown on this, because registrations are finite.
        ApiLog.event(modId, "tilt source registered (priority " + priority + ")");
        TiltSources.add(modId, priority, source);
    }

    @Override
    public void addConditions(AcsConditions conditions) {
        Objects.requireNonNull(conditions, "conditions");
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            ApiLog.warn(modId, "addConditions() does nothing on a dedicated server"
                    + ": the tilt is computed client-side");
            return;
        }
        ApiLog.event(modId, "frame conditions registered");
        Conditions.add(modId, conditions);
    }

    /**
     * A summary line every thirty seconds under {@code DEBUG_MESSAGES}. Without it, "this mod asks
     * for state forty thousand times a frame" would never surface.
     */
    void logSummaryAndReset() {
        long state = counters[Call.STATE.ordinal()];
        long aimRay = counters[Call.AIM_RAY.ordinal()];
        long vanillaEye = counters[Call.VANILLA_EYE.ordinal()];
        if ((state | aimRay | vanillaEye) == 0L) return;

        AeroCamSync.LOGGER.info("[AeroCamSync] * {}: state() ×{}, aimRay() ×{}, withVanillaEye() ×{}",
                modId, state, aimRay, vanillaEye);
        Arrays.fill(counters, 0L);
    }
}
