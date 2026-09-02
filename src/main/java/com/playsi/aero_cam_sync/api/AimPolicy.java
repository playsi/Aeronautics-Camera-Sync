package com.playsi.aero_cam_sync.api;

/**
 * Decides whether a ray passing through {@code BlockGetter#clip} is an aiming ray.
 *
 * <p>ACS already catches aiming rays on its own: it shifts any ray that starts exactly at the
 * owner's vanilla eye position. That filter is strict on purpose, which is what makes double
 * shifting impossible by construction and keeps other mods' physics out. A policy is for the two
 * cases it gets wrong:
 *
 * <ul>
 *   <li>{@link Decision#SHIFT}: this is my aiming ray, even though it does not start at the eye.
 *       For mods that build the origin their own way;</li>
 *   <li>{@link Decision#KEEP_VANILLA}: this is suspension, wheel or collision maths, leave it
 *       alone, for the rare case where such a ray happens to start exactly at the eye;</li>
 *   <li>{@link Decision#PASS}: not my business, let the normal rule decide. This is the answer for
 *       almost every ray you will see.</li>
 * </ul>
 *
 * <p>Cheap facts are computed first, then policies are asked in registration order, and the first
 * non-PASS answer wins. Two policies disagreeing on the same ray are logged once per session and
 * the first is taken. If everyone passes, the normal rule applies.
 *
 * <p>Read this before registering one: {@code decide} is called from inside {@code clip}, dozens of
 * times per frame. No allocations and no raycasts inside it, decide from the fields of the
 * {@link AimQuery} and return. Mods that register no policy pay one comparison.
 */
public interface AimPolicy {

    enum Decision {
        /** Treat this ray as an aiming ray and shift its origin into the tilted camera. */
        SHIFT,
        /** Leave this ray exactly as it is, even if the normal rule would have shifted it. */
        KEEP_VANILLA,
        /** No opinion: let the next policy, then the normal rule, decide. */
        PASS
    }

    Decision decide(AimQuery query);
}
