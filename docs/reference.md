# Quick start and reference

## Adding the dependency

```gradle
repositories {
    maven { url = "https://api.modrinth.com/maven" }
}

dependencies {
    compileOnly "maven.modrinth:aero_cam_sync:1.4.0"
}
```

The API was introduced in 1.4.0. To pin an exact upload rather than a version number, Modrinth
also serves `maven.modrinth:<project-id>:<version-id>`, which for this project is
`maven.modrinth:ZGxtWu73:<version-id>`.

One thing that is easy to get wrong: **Sable is not required on your compile classpath.** ACS
needs it at runtime, but the API is split so that you do not. Everything except
`AcsClientState.tiltSubLevel()` compiles against NeoForge and the ACS jar alone. Add Sable only
if you call that one method.

### At runtime, depend softly

Declare the dependency as optional, with an explicit order:

```toml
[[dependencies.yourmod]]
    modId="aero_cam_sync"
    type="optional"
    versionRange="[1.4.0,)"
    ordering="AFTER"
    side="BOTH"
```

`ordering="AFTER"` is not decoration. `type="optional"` only makes ACS non-mandatory. Without an
explicit order, load order is undefined, and `ModList.isLoaded` can answer correctly while the
API is not ready yet.

Then check before the first call, and **keep the call in a separate class**:

```java
@Mod(YourMod.MODID)
public class YourMod {
    public YourMod(IEventBus modEventBus) {
        if (ModList.get().isLoaded("aero_cam_sync")) {
            AcsBridge.init();     // the only mention of ACS, in a class of its own
        }
    }
}
```

```java
public final class AcsBridge {
    private static AcsHandle acs;

    static void init() {
        acs = AeroCamSyncApi.forMod(YourMod.MODID);
        acs.addListener(...);
    }
}
```

The reason for the split is *when* classes load. Java loads a class on first use, not when
something merely references it, so while the branch above is not taken, `AcsBridge` is never
touched and the ACS types are never resolved. Put both in one class and the behaviour gets
subtle: fields and signatures resolve lazily and may not fail, while a method call will. Whether
it works would depend on which line executed, and it would break on the next harmless edit.

There is no `isPresent()` in this API. If you reached one of these classes, ACS is
already loaded.

### Compatibility promise

Public is **exactly** the package `com.playsi.aero_cam_sync.api`. Everything else, including
`ClipNet`, `PickScope`, `CameraController`, `TiltAccess` and every mixin, is internal and changes
without notice, patch releases included.

Within `1.x` the signatures in the public package do not break. Anything removed is
`@Deprecated` for at least one minor release first.

---

## Your first call

```java
public final class AcsBridge {
    private static AcsHandle acs;

    static void init() {
        acs = AeroCamSyncApi.forMod(YourMod.MODID);
    }
}
```

One handle per mod id, kept in a static field. From there, three things you might want:

| I want to | do this | explained in |
|---|---|---|
| aim where the player really aims | `ACS.state(player, pt).aimRay(reach)` | [troubleshooting.md](troubleshooting.md#ask-for-the-ray) |
| decide the tilt myself | `ACS.addTiltSource(priority, source)` | [tilt-control.md](tilt-control.md#driving-the-tilt-yourself) |
| change what ACS does per frame | `ACS.addConditions(conditions)` | [tilt-control.md](tilt-control.md#conditions-on-a-frame) |

---

## Reference

Everything public lives in `com.playsi.aero_cam_sync.api`.

### AeroCamSyncApi

```java
static AcsHandle forMod(String modId)
```

One handle per mod id. The same id returns the same object, so a static field is fine. The id is
what appears in the ACS log next to everything you ask for, so pass your real mod id. Throws
`IllegalArgumentException` on a null or blank id.

### AcsHandle

| method | notes |
|---|---|
| `String modId()` | the id this handle was created for |
| `AcsState state(Player, float partialTick)` | consistent snapshot, pure read, `partialTick` ignored on the server |
| `void suppress(long millis)` | takes or extends **your** lease. The only all-or-nothing lever, and the only thing here on a clock. No-op on a dedicated server |
| `void release()` | drops **your** lease only |
| `boolean isSuppressed()` | is anyone suppressing |
| `boolean isSuppressedByMe()` | are you |
| `void withVanillaEye(Runnable)` | scope with the real eye, client main thread |
| `<T> T withVanillaEye(Supplier<T>)` | same, with a return value |
| `void addListener(TiltListener)` | register once during setup |
| `void addPolicy(AimPolicy)` | register once during setup, read the cost rules below |
| `void addTiltSource(int priority, TiltSource)` | register once during setup, higher priority is asked first, no-op on a dedicated server |
| `void addConditions(AcsConditions)` | register once during setup, no-op on a dedicated server |

There are no global switches on the handle. Camera collision, third person and standing the
built-in tilt down are [frame conditions](tilt-control.md#conditions-on-a-frame). They used to be
switches held from startup, and that let a mod assert something about the world long after it
stopped being true.

### AcsState

Works on both sides.

| method | notes |
|---|---|
| `boolean modEnabled()` | enabled in config and by the player's toggle |
| `boolean tiltApplied()` | a tilt is measurably affecting aim **right now** |
| `boolean suppressed()` / `List<String> suppressedBy()` | who holds a lease, order not guaranteed. Not every entry is a mod id: `aero_cam_sync:suppress-all` means the player forced it from the ACS config, for diagnosis |
| `Quaternionf posTilt()` / `lookTilt()` | raw rotations, or `null` when that half is not being applied |
| `Vec3 vanillaEye()` / `aimEye()` | |
| `Vec3 vanillaLook(float)` / `aimLook(float)` | recomputed from raw pitch and yaw, so any `partialTick` is valid |
| `AcsRay vanillaRay(double)` / `aimRay(double)` | |
| `AcsClientState client()` | `null` on a dedicated server |

Exactly three things return `null`, and each means "there is no such thing" rather than "there is
no tilt": `posTilt()`, `lookTilt()`, `client()`.

### AcsClientState

Client only. Reached through `AcsState.client()`.

| method | notes |
|---|---|
| `Vec3 vanillaCameraPos()` / `cameraPos()` | captured once per frame in `Camera#setup`, before the tilt is applied |
| `Quaternionf vanillaCameraRot()` / `cameraRot()` | same |
| `float tiltScale()` | `1` in the open, falling to `0` with the camera against a wall. Stays `1` in third person and when collision is off |
| `boolean firstPerson()` | the camera mode this snapshot was taken in. It does **not** tell you whether anything is being corrected, `tiltApplied()` does |
| `String tiltSource()` | the mod whose source drove this frame, or `null` when nobody claimed it, which does not mean there is no tilt, only that it is the baseline's |
| `ClientSubLevel tiltSubLevel()` | the sub-level the tilt is computed from, chosen by ACS's own downward rays and always the local player's. **The only method needing Sable on your classpath** |

Conditions are not reported here, and cannot be. They are frame-scoped, and a snapshot taken
outside the frame they were stated on has nothing to say about them. That is what
`AcsConditions.frameResolved` and [`FrameReport`](#acsconditions-conditioncontext-frameconditions-framereport)
are for.

Camera values are written once per frame, late. Ask earlier in the frame and you get last frame's
values. They are stored, not reconstructed, for the reason given in
[Common symptoms](troubleshooting.md#common-symptoms).

### AcsRay

`from()`, `to()`, `direction()`, the shape you put into a `ClipContext`.

### TiltListener

All methods have defaults, override what you need. Events fire on transitions and not per
frame: stepping onto a sub-level gives exactly one `onTiltStart`.

```java
default void onTiltStart(AcsState state) {}
default void onTiltStop(AcsState state) {}
default void onSuppressionChanged(boolean suppressed, List<String> by) {}
```

`onTiltStop` arrives some frames *after* its cause, when the residual tilt drops below the
threshold, not at the moment the player left the surface.

There are no sub-level events. Which sub-level a player is on is Sable's business,
and the one the local camera is tilting on is in `client().tiltSubLevel()`.

### TiltSource

```java
boolean appliesTo(TiltContext context);              // required
Quaternionf tilt(TiltContext context);               // required, null passes the frame on
default Vec3 eyeOffset(TiltContext context);         // Vec3.ZERO
default boolean eyeOffsetIsDeliberate(TiltContext);  // false
```

`TiltContext` carries `player()`, `partialTick()`, `deltaTicks()`, `surfaceNormal()` (nullable,
the raw input the built-in tilt was computed from), `acsTilt()` (the ACS answer, before anyone was
asked), `firstPerson()`, `vanillaCameraPos()` and `cameraPosFor(Quaternionf)`. It is valid for the
call only, and the quaternions and vectors handed out are copies.

**Do not call `ACS.state(...)` from inside a source.** The snapshot reports the resolved tilt,
resolving asks the sources, and a source asking for the snapshot closes that loop. The result is
a `StackOverflowError` rather than a wrong number. `TiltContext` exists so there is never a
reason to.

`eyeOffsetIsDeliberate` is asked of the winner, and only when the offset actually exceeds the
four-block ceiling, so on almost every frame it is not called at all. It states something about
*your* vector, which is why it lives here rather than in `FrameConditions`: a frame-wide answer
would let one mod's honest reason uncap another mod's mistake, and the mistake is what the
ceiling is for.

See [Driving the tilt yourself](tilt-control.md#driving-the-tilt-yourself).

### AcsConditions, ConditionContext, FrameConditions, FrameReport

```java
default void conditionsFor(ConditionContext context, FrameConditions conditions) {}
default void frameResolved(FrameReport report) {}
```

Both default to no-ops, implement the one you need. Both run on the client render thread, once
per frame, in registration order.

`ConditionContext` gives `player()`, `partialTick()`, `firstPerson()`. It is small,
because it is handed out before anything about the frame has been decided.

`FrameConditions` gives `skipBaseline(reason)`, `skip(modId, reason)`,
`baselineInThirdPerson(reason)`, `takeOverCameraCollision(reason)`. Reasons are mandatory and
non-blank. They are idempotent within a frame, and calling one twice only replaces the reason.

`FrameReport` gives `player()`, `partialTick()`, `firstPerson()`, `tiltSource()` (nullable),
`baselineActive()`, `tilt()`, `eyeOffset()`, `tiltScale()`, `skipped()`. The pose values are the
ones actually applied, after the wall clamp, which is what the rays and the server were given.

`baselineActive()` is true when nobody claimed the frame **and** the built-in policy was allowed
to run: no `skipBaseline`, no suppression lease, and a camera mode ACS tilts in. With
`tiltSource()` null and this false, the frame carried nobody's tilt.

Both objects are valid for the call only. See
[Conditions on a frame](tilt-control.md#conditions-on-a-frame).

### AimPolicy and AimQuery

```java
enum Decision { SHIFT, KEEP_VANILLA, PASS }
Decision decide(AimQuery query);
```

`AimQuery` gives you `player()`, `from()`, `to()`, `context()` (nullable) and `startsAtEye()`,
which is whether the built-in rule matched. It is valid only for the duration of the call, so do
not keep it.

Policies are asked in registration order and the first non-`PASS` answer wins. If two policies
disagree about the same ray, ACS logs both once and takes the first.

Both decisions have an equivalent that costs no dependency at all. `KEEP_VANILLA` is what you
get by naming a non-player entity in your `ClipContext`, and `SHIFT` is what you get by building
the origin from Sable's eye rather than the vanilla one. A policy is worth registering when
neither fits: an aiming ray that has to be cast on the player's behalf, from an origin you
cannot move yourself. See
[Fixing it without the API](troubleshooting.md#fixing-it-without-the-api).
