/**
 * Public API of Aeronautics Camera Sync.
 *
 * <p>ACS tilts the camera to match the Sable sub-level (usually a Create Aeronautics contraption)
 * the player stands on. The camera moves as well as rotates, so the point they aim from stops
 * being {@code player.getEyePosition()}: it is that point rotated around their feet, and at a
 * noticeable roll the two are up to a block apart.
 *
 * <p>Most mods need none of this. ACS catches aiming rays inside {@code BlockGetter#clip} and
 * shifts them already. This API is for the rays it cannot catch, for mods that want to know what
 * ACS is doing, and for mods that need it to stand back for a while.
 *
 * <pre>{@code
 * private static final AcsHandle ACS = AeroCamSyncApi.forMod("mymod");
 * }</pre>
 *
 * <p>One handle per mod, safe in a static field; everything else hangs off it. See
 * {@link com.playsi.aero_cam_sync.api.AcsHandle}, and {@code docs/} for the guides.
 *
 * <p>There is no {@code isPresent()} check here: if you reached these classes, ACS is loaded.
 * Guard before the first call instead, and keep that call in a separate class so the classloader
 * does not touch these types too early:
 *
 * <pre>{@code
 * if (ModList.get().isLoaded("aero_cam_sync")) AcsBridge.init();
 * }</pre>
 *
 * <p>This package, and only this package, is public. Everything else under
 * {@code com.playsi.aero_cam_sync} is internal and changes without notice, patch releases
 * included. Within 1.x the signatures here do not break, and anything removed is deprecated for
 * at least one minor release first.
 *
 * <p>The tilt is computed on the client. {@link com.playsi.aero_cam_sync.api.AcsState} works on
 * both sides and its client half is behind
 * {@link com.playsi.aero_cam_sync.api.AcsState#client()}, which is null on a dedicated server.
 * {@link com.playsi.aero_cam_sync.api.AcsHandle#withVanillaEye(Runnable)} and the aiming pipeline
 * are client main thread only. Rays from background threads are left alone on purpose (a sound
 * mod tracing from a thread pool deadlocked the client once), so calls from other threads are
 * no-ops with a single warning.
 */
package com.playsi.aero_cam_sync.api;
