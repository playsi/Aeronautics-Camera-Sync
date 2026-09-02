# Something in my mod broke

Start with [the quick diagnostic](API.md#quick-acs-diagnostic) if you are not sure the problem is
ACS at all. Everything below assumes it is.

Almost everything on this page has the same cause. If your symptom is not in the list, or a fix
does not hold, read [Where the bug comes from](#where-the-bug-comes-from) and work from there.

## Check that the tilt is the ACS tilt

A tilt on screen is not proof that ACS decided it. Another mod can drive the pose through
[tilt sources](tilt-control.md#driving-the-tilt-yourself), and on the frames it claims, the
rotation your rays follow is that mod's and not the ACS surface tilt. The behaviour you are
debugging may then be theirs, and so is the fix.

Three ways to tell them apart, from cheapest to most certain.

- [`AcsClientState.tiltSource()`](reference.md#acsclientstate) names the mod whose source drove
  the frame, and is `null` when nobody claimed it. `null` does not mean there is no tilt. It
  means the tilt is the ACS baseline.
- The log names it once per session, on the line
  `* somemod: tilt source took the frame (priority N)`. If that line is there, at least some of
  your frames are not ACS's.
- The player's ACS toggle stops the ACS tilt and, by design, does not stop a mod driving the
  frame. `Suppress All` in the ACS config stops both. If the symptom survives the toggle but goes
  with `Suppress All`, a foreign source is involved.

This matters before you file anything. A report that says "ACS broke my mod" is answered
differently when a third mod was deciding the pose the whole time.

If ACS carries a specific fix for the mod you are debugging, it is listed with its reasoning in
[compatibility.md](compatibility.md).

---

## Common symptoms

**"My tool aims at or interacts with the wrong block."**

First check that you are on the current release of ACS. Older builds correct fewer cases, and
this is the case that changed most between them.

If that is not it, the answer depends on how your tool builds its ray.

- It calls `level.clip()` from the player's eye. Already corrected, nothing to do.
- It calls `level.clip()` from an origin of your own. ACS sees the call but does not recognise
  the ray as aiming, so it leaves the origin where you put it. Without the dependency, add
  Sable's eye difference to your origin yourself:
  [Take the origin from Sable](#take-the-origin-from-sable-instead-of-vanilla). With the
  dependency, register an [`AimPolicy`](reference.md#aimpolicy-and-aimquery), a per-ray callback
  that tells ACS what to do with a ray, and answer `SHIFT` for that one. See
  [Tell ACS how to treat your ray](#tell-acs-how-to-treat-your-ray).
- It walks blocks itself and never calls `clip`. ACS never sees the ray at all. Ask for the
  corrected ray instead: [`AcsState.aimRay`](reference.md#acsstate), described in
  [Ask for the ray](#ask-for-the-ray).

**"My preview or outline is drawn at an offset."**

Preview code often traverses blocks by hand rather than calling `level.clip()`, stepping from a
start point along a direction until something stops it. ACS only intercepts rays that pass
through `clip`, so a traversal like that is invisible to it. The direction it was handed is
already rotated, the start point is not, and the preview is drawn along a ray parallel to the
right one and offset from it.

A policy will not help here. An
[`AimPolicy`](reference.md#aimpolicy-and-aimquery) is asked from inside `clip`, and this code
never reaches `clip`.

Two ways out.

- **Without the API.** If the traversal exists only to find the first thing hit, `level.clip()`
  does that, and a clip from the eye is corrected for you.
- **With the API.** If you genuinely need to walk blocks, take the corrected start point and
  direction from [`AcsState.aimRay`](reference.md#acsstate) and traverse from those.

**"A focus point, marker or anchor placed at the eye is in the wrong place."**

That is a point, not a ray, so there is nothing for ACS to intercept. Read the aiming eye from a
snapshot: [`AcsState.aimEye()`](reference.md#acsstate).

**"Physics or collision probes started behaving oddly once ACS was installed."**

The opposite problem. A probe that happens to start exactly at the player's eye looks to ACS
like an aiming ray, so its origin gets moved with the camera, which is not what a probe wants.

The fix without the dependency is to name the right owner. ACS decides whose ray it is from the
`ClipContext`, so a probe that names the machine rather than the player is never a candidate.
See [Say the ray is not the player's aim](#say-the-ray-is-not-the-players-aim).

If the probe genuinely has to be cast on the player's behalf, register an
[`AimPolicy`](reference.md#aimpolicy-and-aimquery) that answers `KEEP_VANILLA`, matched on the
`ClipContext` your own code created.

Do not narrow it by distance instead. Filtering by "rays that start within N blocks of the
player" was tried inside ACS in an earlier version, and it caught exactly this kind of probe.
That is the bug you are reporting, seen from the other side. Match on your own object.

**"Projectiles or thrown items leave at an offset."**

A spawn point is a ray origin like any other. Throws with the vanilla shape are corrected for
you. A projectile your own code spawns from `player.getEyePosition()` is not, because that
method returns the untilted eye.

Take both halves from one snapshot: [`AcsState.aimEye()`](reference.md#acsstate) for where it
leaves from, and [`AcsState.aimLook(partialTick)`](reference.md#acsstate) for which way it goes.
`aimLook` is the view vector recomputed from the tilted pitch and yaw, so any `partialTick` you
pass to it is valid.

On a server without ACS installed there is nothing to correct the flight server-side, so ACS
drops the tilt on the client while the player holds an item whose use is resolved by a raycast,
rather than let the two sides disagree. A projectile that is correct in singleplayer and wrong
on a server is usually this.

**"I render something from the eye and it looks wrong."**

The eye position itself is untouched. `player.getEyePosition()` returns the real, untilted eye
with or without the API. What ACS moves is the origin of rays cast from it, so render work that
only reads a position already has the right one.

Render work that casts something is different. A light probe, a visibility clip or a nameplate
placement ray is corrected like any other ray, and the point you want there is the true eye, not
the aiming point. Wrap that work in [`AcsHandle.withVanillaEye`](reference.md#acshandle), which
turns the correction off for the duration of the call. This is the one case here that needs the
dependency and has no equivalent without it.

**"I need the untilted camera position."**

[`AcsClientState.vanillaCameraPos()`](reference.md#acsclientstate), captured once per frame
before ACS touches it.

Do not try to recover it by inverting the tilt quaternion. The applied tilt is scaled by wall
proximity, and that scale changes during the frame, so inverting by the current value does not
undo what was applied. ACS stores the vanilla values rather than reconstructing them, and so
should you.

**"I inject into `GameRenderer#pick` and overwrite the hit result."**

That still works. Since 1.4.0 ACS no longer overwrites anyone's pick result. It sets up the
conditions and lets everyone compute inside them, so order between ACS and your injection is not
something either side has to rely on.

---

## Ways to build a ray, and which of them work

Every row here is a case that has actually come up.

| how you build it | result |
|---|---|
| `getEyePosition()` + `getViewVector()` + `level.clip()` | works, nothing to do |
| eye assembled by hand + `level.clip()` | works, it is the same point bit for bit |
| Sable's interpolated eye helper + `level.clip()` | works |
| **your own origin** + Sable's eye difference added | works, [no dependency needed](#take-the-origin-from-sable-instead-of-vanilla) |
| `mc.hitResult` | works |
| **your own origin** (not the eye) + `level.clip()` | not corrected, use [`AimPolicy.SHIFT`](reference.md#aimpolicy-and-aimquery) |
| **your own block traversal**, no `clip` at all | invisible to ACS, use [`aimRay`](reference.md#acsstate) |
| **a point, not a ray** | invisible to ACS, use [`aimEye`](reference.md#acsstate) |
| **a physics probe** that happens to start at the eye | wrongly corrected, use [`AimPolicy.KEEP_VANILLA`](reference.md#aimpolicy-and-aimquery) |
| a probe naming a **non-player entity** in its `ClipContext` | never corrected, [which is what a probe wants](#say-the-ray-is-not-the-players-aim) |

---

## Fixing it without the API

Most of what reaches ACS is fixed here, and none of it requires the dependency.

### Do nothing

If your ray goes through `clip` and starts at the eye, stop here. This is the intended outcome
for most mods, and it is why the API is small.

### Say the ray is not the player's aim

ACS works out whose ray it is from the `ClipContext` itself. A ray is a candidate for correction
only when the entity named in that context is the player whose camera is tilted. So a probe that
is not about the player's aim can say so by naming what it is actually about:

```java
// looks like aiming: the player is named, and the ray starts at their eye
level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, player));

// left alone: this probe is about the machine's geometry, so the machine is named
level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, myVehicle));
```

ACS never touches the second one. For a suspension probe, a wheel cast or a collision feeler
that happens to start at the player's eye, that is the whole fix, and it needs no dependency.

Name an entity. `CollisionContext.empty()` is a last resort and not the short way to do this.
The other `ClipContext` constructor takes a `CollisionContext` directly, and
`CollisionContext.empty()` carries no entity, so it does keep ACS away:

```java
level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, CollisionContext.empty()));
```

It also changes the clip. The collision context is what resolves block shapes that answer
differently per entity, so an empty one makes those blocks report a different shape and your
probe hits something else. You have traded an aiming bug for a geometry bug, and the second one
is harder to see. Reach for it only when the probe genuinely belongs to nobody, and if you are
casting on behalf of a machine, a wheel or a seat, name that.

### Take the origin from Sable instead of vanilla

`player.getEyePosition()` is the untilted eye, which is why a ray built from it needs correcting
at all. Sable has its own eye accessor, and it already carries every rotation applied to the
player's view, including ACS's:

```java
Vec3 eye = Sable.HELPER.getEyePositionInterpolated(player, partialTick);
```

Build your ray from that and there is nothing left to correct. The ACS filter looks for rays
starting at the *vanilla* eye, so this one is passed over, which is the right answer: it already
starts where it should.

For an origin that is not the eye, take the difference and apply it to your own point. This is
Sable's own idiom rather than anything ACS invented:

```java
Vec3 delta = Sable.HELPER.getEyePositionInterpolated(player, partialTick)
        .subtract(player.getEyePosition(partialTick));
Vec3 origin = myOwnPoint.add(delta);
```

Two limits. It is a client value, so it is for rendering, previews and client-side picking, not
for anything the server settles. And the difference holds everything Sable and ACS between them
do to the eye, not the ACS rotation on its own. If you need those separated, that is what
[`AcsState`](reference.md#acsstate) is for.

### Your own distance metric is your own

If you compare distances to decide which hit wins, those numbers are yours and ACS does not
touch them. A metric that measures to a sub-level in world coordinates is measuring the wrong
thing, and that is Sable's geometry to answer.

---

## Fixing it through the API

Add the dependency first, see [reference.md](reference.md#adding-the-dependency).

### Ask for the ray

Take a snapshot, read what you need:

```java
AcsState state = ACS.state(player, partialTick);

AcsRay ray = state.aimRay(player.blockInteractionRange());
BlockHitResult hit = level.clip(new ClipContext(
        ray.from(), ray.to(), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
```

Take one snapshot per frame and read from it. ACS state changes *within* a frame: the pick
window opens and closes, and wall scaling is recomputed in `Camera#setup`. A mod that asks "am I
tilted?" at one point and "by how much?" at another can get an inconsistent pair, an untilted
origin with a tilted direction. A snapshot cannot be inconsistent with itself.

`state()` is a pure read: no clips, no writes, no side effects. Calling it is cheap, but it does
allocate, so take it per frame rather than per question.

### Tell ACS how to treat your ray

Some rays go through `clip` but do not start at the eye, so the built-in filter does not
recognise them. Others are not aiming rays at all yet start exactly at the eye, so the filter
recognises them wrongly. An [`AimPolicy`](reference.md#aimpolicy-and-aimquery) fixes both. It is
asked once per ray and answers one of three things: `SHIFT` to move the origin into the tilted
camera, `KEEP_VANILLA` to leave the ray alone, `PASS` to say nothing about it.

```java
private static ClipContext myRay = null;

ACS.addPolicy(query -> query.context() == myRay
        ? AimPolicy.Decision.SHIFT
        : AimPolicy.Decision.PASS);

// ... and where you cast it:
ClipContext ctx = new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
myRay = ctx;
try {
    return level.clip(ctx);
} finally {
    myRay = null;
}
```

Three rules, and the first is not optional.

- Recognise your own ray and answer `PASS` to everything else. Your policy is asked about
  every ray the player casts while tilted: suspension, collision, other mods' aiming. A policy
  that answers for all of them will break the game in ways that look like an ACS bug. Matching
  on the `ClipContext` you created yourself, as above, cannot collide with anyone.
- No allocations and no raycasts inside `decide`. It runs from inside `clip`, dozens of
  times per frame. Decide from the fields of the query and return.
- A policy cannot create a tilt. If there is no active correction, ACS never asks. `SHIFT`
  chooses *which* rays move, not *whether* there is anything to move them by.

Mods that register no policy pay nothing: an empty policy list costs one comparison.

### When you want the untilted eye

Two different needs, two different tools.

**For calculations**, take the values as if ACS were not installed:
[`vanillaEye()`](reference.md#acsstate), `vanillaLook(partialTick)`, `vanillaRay(reach)`.

**For rendering**, wrap the work instead:

```java
ACS.withVanillaEye(() -> renderNameplate(entity));
int light = ACS.withVanillaEye(() -> computeLight(pos));
```

Inside the scope the player's eye is the real one and ray correction is off, so anything you
clip in there is untouched too. This is what lighting probes, entity culling and nameplate
placement want: the true eye, not the aiming point.

The scope form cannot be left unbalanced, and it nests. It is client main thread only. Called
from another thread the body still runs, just without the scope, and ACS logs one warning.

---

## Problems that are Sable's, not ACS's

Both of these look like ACS bugs and are not. They come from the sub-level being a separate
level with its own coordinate space, which is Sable's design and predates ACS.

### A hit on a sub-level comes back in sub-level coordinates

When your clip hits the contraption rather than the world, the returned `BlockHitResult` is in
the sub-level's **own** coordinate space, which lives millions of blocks away:

```
player at (-1583, 69, 1346)
hit on the sub-level:  BlockPos(20481031, 130, 20524045)
a miss:                Vec3(-1580.103, 68.704, 1348.985)     <- world coordinates
```

That is Sable projecting the ray into the contraption's level. It only shows up when you
actually hit the sub-level, so it survives testing on the ground and then reads as an ACS bug in
the field. Code that treats such a position as a world position is what crashes.

If you need world coordinates, convert with the sub-level's pose (`Pose3dc#transformPosition`).
Sable's API is the reference for that.

### Crashes inside a pick

Look at the coordinates in the stack first. A `BlockPos` in the tens of millions is not
corruption, it is the case above.

If that is not it, the useful report is the full stack, what the player was standing on, the
camera mode, and whether any mod that drives the tilt was installed.

---

## Diagnosing a problem

Everything a foreign mod asks of ACS is logged with a star and the mod id. In a report that
says "ACS broke my mod", these lines answer who called ACS at all:

```
[AeroCamSync] * yourmod: api handle acquired
[AeroCamSync] * yourmod: aim policy registered
[AeroCamSync] * yourmod: tilt source registered (priority 100)
[AeroCamSync] * yourmod: frame conditions registered
[AeroCamSync] * yourmod: tilt suppressed for 250 ms
[AeroCamSync] * yourmod: camera collision taken over: rotates the player, keeps its own hitbox
[AeroCamSync] * yourmod: tilt source took the frame (priority 100)
[AeroCamSync] * othermod: skipped tilt source of yourmod: its shake fights the surface lean
```

Each appears once per session, so the log does not drown. That includes the last one, which is
logged per pair of mod ids and is the only thing that answers "why did my source stop winning in
this modpack" from a log alone.

For anything per-frame, the log is the wrong tool. Conditions and skips exist for one frame
at a time. Implement `AcsConditions.frameResolved` and read the
[`FrameReport`](reference.md#acsconditions-conditioncontext-frameconditions-framereport) instead.
Its `tiltSource()`, `baselineActive()`, `tiltScale()` and `skipped()` describe the frame that
just happened, which is the granularity these questions are actually asked at.

Turn on `Debug messages` in the ACS config for more: which callers the ray net caught, what
your policies decided, and a summary every thirty seconds of how often you called in. That last
one is useful if you suspect you are taking a snapshot far more often than you meant to.

Three questions that get confused with each other:

**"Is a tilt involved at all?"** Turn on `Suppress All`, at the top of the Debug tab in the ACS
config. It forces the tilt off for everyone, including a mod driving it through the API, which
the player's own toggle does not do. If the symptom survives that, no tilt of any origin is
causing it.

**"Is ACS involved at all?"** `Suppress All` silences the tilt, not the mod: while ACS is loaded
its mixins are woven either way. Take the jar out of the mods folder. That is the only answer
that is never ambiguous.

**"Is a tilt active at the exact moment my symptom happens?"** Read
[`AcsState.tiltApplied()`](reference.md#acsstate). Do not use the player's ACS toggle to answer
this: it stops the built-in tilt but not a mod driving the frame through the API, so switching
it off proves nothing.

When reporting something to ACS, the useful attachment is the log with `Debug messages` on, plus
what the player was standing on and whether they were in first or third person.

---

## Where the bug comes from

### The camera is rotated, the player is not

ACS rotates the player's camera. It does not rotate the player entity. To the server, and to
every mod that asks vanilla where the player is, they are standing upright on a flat block as
always. ACS fakes the lean and then drags every "from the eye" ray along with it, because that
is the most a client-side mod can do without desyncing from the server.

So ACS owns one half of an illusion:

| | who owns it |
|---|---|
| what the player **sees**: camera, crosshair, picking, projectiles, thrown items | ACS |
| what the player **is**: model, hitbox, the server's opinion | vanilla, unchanged |

Standing on a sub-level is only the usual way to get a rotated camera, not the condition for any
of this. A sub-level that is level tilts nothing and breaks nothing, and a mod driving the tilt
through the API produces every symptom on this page with no sub-level in sight. If a fix works
on the ground and fails in the air, look at how far the camera is leaning, not at what is under
the player's feet.

### A ray has two halves, tilted by different rules

This is the cause of nearly every "ACS broke my mod" report so far.

| half of the ray | tilted where |
|---|---|
| **direction**: `getViewVector`, `getLookAngle` | always, everywhere |
| **origin**: the eye | only where ACS manages to correct it |

Both halves tilted is correct. Both halves vanilla is also correct, just uncorrected. The
failure is the mix. Take the direction from a place ACS always reaches and the origin from a
place it never sees, and you get an untilted start with a tilted direction: a ray parallel to
the right one and offset from it.

It still hits *a* block, so ordinary interaction looks roughly fine and the bug hides. It stops
hiding the moment precision matters. The case that produced this section was a control panel
whose buttons could be pressed from a wrong angle and not from the right one, with the error
growing as the camera leaned further.

When the camera tilts, it rotates around the player's feet, and the eye goes with it. At a
noticeable roll the aiming point and `player.getEyePosition()` are up to a block apart.

### What already works, with no changes on your side

- **Any ray through `level.clip()` or `BlockGetter#clip`.** ACS intercepts it and moves the
  origin into the tilted camera. This covers most mods, including ones that build the eye by
  hand: `position().add(0, getEyeHeight(), 0)` produces bit for bit the same point as
  `getEyePosition()`, so it matches too.
- **`Minecraft.getInstance().hitResult`**, the vanilla pick, already corrected.

### What ACS cannot see

- **Block traversal written by hand.** If you walk blocks yourself instead of calling `clip`,
  the ray never reaches ACS. Nothing ACS does can fix it and no policy will help. Ask for the
  origin: [`AcsState.aimRay`](reference.md#acsstate).
- **Reference points that are not rays**, such as a focus point, a marker or an anchor. Use
  [`AcsState.aimEye`](reference.md#acsstate).
- **Your own distance metric.** If you compare distances to decide which hit wins, the numbers
  you compare are yours and ACS does not touch them.

### If you read the tilt, it is a camera value

Never treat the tilt ACS reports as the player's real orientation. It is a *camera* value:
smoothed over several frames, and near a wall in first person it is scaled down. It lags
the surface under the player and sometimes understates it on purpose.

If what you need is "how is this contraption oriented", ask Sable for the sub-level pose. That
is where ACS gets it from too. What ACS answers is "where is the player looking", nothing more.

### If your mod manages the player's orientation itself

Then you own the other half, and two of ACS's assumptions stop being true. Both have an answer
in [tilt-control.md](tilt-control.md). Hand ACS your rotation with
[a tilt source](tilt-control.md#driving-the-tilt-yourself) instead of leaving two mods to
raycast the same floor by their own clocks. And take the wall check over with
[`takeOverCameraCollision`](tilt-control.md#taking-over-camera-collision), because that
check measures from the vanilla eye, which for a genuinely rotated player is a point that is not
there.

The trap in this area is owning half of each side. A mod that rotates the model but not the
hitbox, or corrects the camera but not the rays, produces a state that looks correct in the one
scenario it was tested in and wrong everywhere else. Two such mods then quietly fight, each
fixing what the other broke. Decide which half you own, do that half completely, and declare it
per frame so the other half can stand aside. Conditions are there to say who is responsible for
what, on the frames where it is true, which is why none of them is a feature flag.

### The tilt is not always there, and your code should not branch on it

`aim*` values equal `vanilla*` whenever there is nothing to correct: nobody is standing on a
tilted surface and no mod's tilt source has claimed the frame, or a nearby wall has scaled the
tilt to zero. The player turning ACS off, or being in third person, is not on that list by
itself. Both stop the *baseline* only, and a mod driving the frame is unaffected by either.

This is decided per frame, and a mod may decide it differently on every frame. A tilt source
can claim one frame and decline the next, and conditions are stated for a single frame with no
residue. There is no state here to cache and no steady mode to detect.

So write [`state.aimRay(reach)`](reference.md#acsstate) once and do not branch on it. When there
is no tilt it returns the vanilla ray, which is the ray you would have built anyway. A mod that
asks "is there a tilt?" and takes a different path is a mod with two code paths, one of which is
rarely exercised.

Two subtleties worth knowing.

- **Suppression is not instant.** After a mod calls [`suppress`](reference.md#acshandle), the
  camera eases back to level over the normal smoothing time instead of snapping. During those
  frames `suppressed()` is already `true` while `aim*` still differs from `vanilla*`, because
  the camera really is still tilted. For "is there a tilt right now", use `tiltApplied()`, never
  `suppressed()`.
- **Third person is not a single answer.** The baseline does nothing back there unless somebody
  states [`baselineInThirdPerson`](tilt-control.md#third-person-and-the-baseline) for the
  frame, but a claimed frame works in third person with no condition at all. So
  `client().firstPerson()` does not tell you whether anything is being corrected. Ask
  `tiltApplied()`, which answers about the frame rather than about the camera mode.
