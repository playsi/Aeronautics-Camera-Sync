package com.playsi.aero_cam_sync.api;

import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

/**
 * Everything one mod can ask of ACS. Obtained from {@link AeroCamSyncApi#forMod(String)}.
 *
 * <p>The handle carries your mod id. That is what makes a suppression lease owned, and what puts
 * your name next to your calls in the ACS log.
 *
 * <p>Three entry points, and {@code docs/tilt-control.md} covers when to reach for which:
 * {@link #state} reads and changes nothing, {@link #addTiltSource} drives the tilt on the frames
 * you claim, and {@link #addConditions} states per frame what ACS may and may not do.
 * {@link #addPolicy} sits outside those three because it answers about a single ray rather than a
 * frame, from inside {@code clip}, with the opposite cost profile.
 *
 * <p>Camera collision, third person and standing the ACS tilt down were switches held from startup
 * once. They are frame conditions now: a mod that rotates the player is not rotating them while
 * they stand on flat ground. {@link #suppress(long)} is the one lever that is still not
 * frame-scoped, because a cutscene runs on a clock.
 */
public interface AcsHandle {

    /** The mod id this handle was created for. */
    String modId();

    /**
     * A consistent snapshot of what ACS is doing right now.
     *
     * <p>A snapshot, not a pile of getters, because the state changes within a frame: ask
     * "am I tilted?" at one point and "by how much?" at another and you get an untilted origin
     * with a tilted direction, which is the bug class this API exists to remove.
     *
     * <p>It allocates, so take one per frame and read it instead of calling this per question.
     * It clips nothing and writes nothing.
     *
     * @param player      the player to describe; on the client only the local player is
     *                    meaningful, others report no tilt
     * @param partialTick render partial tick, ignored on the server
     */
    AcsState state(Player player, float partialTick);

    /**
     * Stops the tilt for everyone for the next {@code millis} milliseconds. Not just ACS: while a
     * lease is held no {@link TiltSource} is asked at all. This is the cutscene lever, and it is
     * meant to beat whoever is currently driving.
     *
     * <p>Two other things stop a tilt and both are narrower.
     * {@link FrameConditions#skipBaseline(String)} stops ACS deciding while everything else keeps
     * running, per frame. {@link FrameConditions#skip} names one mod for one frame. Picking the
     * wrong one of the three is the usual way to be disappointed by this API.
     *
     * <p>If you came here to switch ACS off and do the tilt yourself, you want {@code skipBaseline}
     * plus a {@link TiltSource}. This switches off the machinery you came for, the ray net and the
     * projectile correction and the packet, and leaves you to write all of it.
     *
     * <p>A lease, not a switch: this takes or extends yours, {@link #release()} drops yours, and
     * the tilt stays suppressed while any mod's lease is alive. You cannot drop someone else's, or
     * the mod whose cutscene ends first cuts short the one still running.
     *
     * <p>The camera eases back to level over the normal smoothing time instead of snapping, and
     * rays, projectiles and the server sync follow it. So right after this call
     * {@link AcsState#suppressed()} is already true while {@code aim*} still differs from
     * {@code vanilla*}. Trust {@link AcsState#tiltApplied()} for "is there tilt".
     *
     * <p>The clock is real time but stops while the game is paused. A lease over ten seconds is
     * allowed and logs a warning with your mod id. All leases drop when the player leaves the
     * world. No-op on a dedicated server, with one warning.
     *
     * @param millis how long from now, in milliseconds; values &le; 0 are ignored
     */
    void suppress(long millis);

    /** Drops this mod's lease. Leases held by other mods are unaffected. */
    void release();

    /** Whether the tilt is suppressed by anyone, including other mods. */
    boolean isSuppressed();

    /** Whether this mod is currently holding a lease. */
    boolean isSuppressedByMe();

    /**
     * Runs {@code body} with the player's eye back at the real, untilted one.
     *
     * <p>For render work that wants the true eye rather than the point the player aims from:
     * lighting probes, entity culling, nameplate placement. Inside the scope the eye correction is
     * off, so the aiming-ray net leaves rays alone too.
     *
     * <p>A scope, not an enter/exit pair, so it cannot be left unbalanced; nesting works.
     * Client main thread only: from any other thread the body still runs, just without the scope,
     * and one warning per session is logged.
     */
    void withVanillaEye(Runnable body);

    /** {@link #withVanillaEye(Runnable)} for a body that returns a value. */
    <T> T withVanillaEye(Supplier<T> body);


    /**
     * Registers a listener for tilt start/stop and suppression changes. Register once during
     * setup. Listeners are called on the thread the event happens on, the client main thread for
     * tilt events.
     */
    void addListener(TiltListener listener);

    /**
     * Registers a policy that decides whether a given ray is an aiming ray. Register once during
     * setup, and read {@link AimPolicy} first: {@code decide} is called from inside {@code clip},
     * dozens of times a frame.
     */
    void addPolicy(AimPolicy policy);

    /**
     * Registers a source that decides the tilt itself, taking it from ACS on the frames it claims.
     *
     * <p>Sources are asked in descending priority order, ties broken by registration order, and
     * the first one to claim the frame wins it. The ACS tilt is what happens when nobody claims,
     * so any priority outranks it. Registering the same source twice registers it twice.
     *
     * <p>Register once during setup, and read {@link TiltSource} first: winning a frame takes over
     * the player's aim, projectiles and interaction reach, on the server as well as here.
     *
     * <p>No-op on a dedicated server, with one warning.
     *
     * @param priority higher is asked first; use 0 unless you have a reason
     */
    void addTiltSource(int priority, TiltSource source);

    /**
     * Registers frame conditions: what ACS may and may not do on each frame, and a report of what
     * it did. Register once during setup, and read {@link AcsConditions} first.
     *
     * <p>Registering the same object twice registers it twice. It may be the same object as your
     * {@link TiltSource}, registered with both calls, and for a mod that drives the tilt and
     * conditions it that is the shape to use: one predicate, so the two cannot drift apart.
     *
     * <p>No-op on a dedicated server, with one warning.
     */
    void addConditions(AcsConditions conditions);
}
