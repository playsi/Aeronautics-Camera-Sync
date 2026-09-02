# Compatibility list

Mods ACS carries a specific fix for, what was wrong in each case, and where the fix lives.

Read it for two reasons. If you maintain one of these mods, this is what ACS does to it and why.
If you are fixing a mod of your own, the cases below are the shapes the same bug takes, and one
of them is probably yours.

The general mechanism, and the fixes that need no compatibility entry at all, are in
[troubleshooting.md](troubleshooting.md). Most mods land there and need nothing on this page.

---

## Create

**Mod id `create`.** Three separate problems, three separate fixes. They share a cause: Create
builds several of its rays from `player.getEyePosition()` and its own view arithmetic, so the
origin stays vanilla while the direction is already tilted.

### Ray direction in `RaycastHelper`

`RaycastHelper#getTraceTarget` builds the far end of a ray from raw `getXRot` and `getYRot`
rather than from the camera. With the camera tilted, every Create interaction built on that
helper diverges from the crosshair. The visible case is the redstone link: the handler misses the
frequency slot, does not cancel the event, and a block is placed instead of the frequency being
set. It happens with rotation alone and with position shift alone.

The origin of that ray needs no fix. It ends in a vanilla `level.clip()` from the vanilla eye,
which is what the general net already catches. Only the direction is corrected here, because it
is computed from raw angles that never reach `clip`.

Fix: [`CreateRaycastTiltMixin`](../src/main/java/com/playsi/aero_cam_sync/mixins/CreateRaycastTiltMixin.java).
Both sides, since the right-click event arrives on both.

### Big outlines

`BigOutlines#pick()` is a separate Create pick for blocks with a large outline, such as rails and
pipes. It does not supplement the vanilla pick, it overwrites `mc.hitResult`, so when you point
at a rail the final aim result is Create's, and where the block lands depends on it.

Its ray is assembled from two sources and only one of them was corrected: the origin is the plain
vanilla eye, the target comes from `RaycastHelper.getTraceTarget`, which is tilted. That is the
hybrid ray exactly. The symptom is "I aim at a rail and the block lands somewhere else", and it
disappears when `Shift camera position` is off, because then the two origins coincide.

Sable has its own compatibility patch for this method, but it fixes only the sub-level half: the
distance metric, the traversal and the shape clip. It does not wrap the eye, and it has no reason
to, because nothing tilts the camera in Sable alone.

The general clip net does not cover this path either, and that was verified rather than assumed:
this code never calls `Level#clip`, it walks blocks itself and clips voxel shapes directly. The
mixin was deleted on an experiment branch as a control case, the net did not catch it, and the
rail symptom came back.

Fix: [`CreateBigOutlinesEyeMixin`](../src/main/java/com/playsi/aero_cam_sync/mixins/compat/CreateBigOutlinesEyeMixin.java).
It routes the vanilla eye call into Sable's eye helper, which supplies both the sub-level pose and
the ACS offset. No arithmetic of its own, and the direction is left alone because it is already
tilted.

### Super glue selection

Placing glue works, because both points come from `mc.hitResult`. Pointing at glue that already
exists, to highlight it or remove it with a left click, works only sometimes on a tilted surface:
the wrong box lights up, or none does.

`SuperGlueSelectionHandler#tick` casts its own ray, vanilla eye plus tilted target, and then
intersects it with the entity's bounding box through `AABB#clip`. It never reaches `Level#clip`,
so the net cannot see it. Correcting the origin also fixes the selection metric as a side effect,
because the nearest box is then measured from the same point the ray was cast from.

The sub-level half is already handled by Sable, which projects the glue box into the world by its
logical pose, so the only thing missing was the origin.

Fix: [`CreateSuperGlueEyeMixin`](../src/main/java/com/playsi/aero_cam_sync/mixins/compat/CreateSuperGlueEyeMixin.java).

What this does not fix: automatic gluing when a block is placed with glue in the off hand.
That path is server-side and clips a wrapper type and not a `Level`, so both the ACS net and
Sable's own clip override pass it by. It does not work on a sub-level without ACS either.

---

## Create: Simulated

**Ships inside Create Aeronautics.** Four fixes, all from the same cause as Create's: rays
assembled half from the vanilla eye and half from a tilted direction.

### Honey glue

Glue is placed where you aim, but the yellow preview frame sits beside the crosshair instead of
under it, and pointing at glue that already exists misses by more the further the camera leans.

Placement is correct because the position comes from the right-click event, which is the pick
result ACS already keeps correct. The preview and the hover are two separate rays the client
handler casts itself, both starting at the vanilla eye.

Neither reached the net, for two different reasons. One does call `Level#clip`, but builds its
`ClipContext` with `CollisionContext.empty()`, so the context names no entity and the owner check
finds nothing. Its start point would have matched the filter. The other clips an `AABB`
directly and never calls `Level#clip` at all.

Fix: [`SimulatedHoneyGlueEyeMixin`](../src/main/java/com/playsi/aero_cam_sync/mixins/compat/SimulatedHoneyGlueEyeMixin.java).
It moves the origin of both rays by the same delta. The direction is left alone, and the helper
that builds the far end rotates it around the origin it was given, so it picks up the shifted one
by itself.

The scroll handler is left untouched. The eye there is not a ray but a "is the camera
inside this box" test that only decides the scroll direction, and it is the one place that
measures against the render pose while the hover measures against the logical one.

### Physics staff, where the beam points

The selection box around a grabbed object sits correctly while the beam itself goes off to the
side, as though the camera were not tilted. That is exactly the split: the box is drawn from the
camera position, and the beam direction is computed from the vanilla eye.

Fix: [`SimulatedStaffBeamMixin`](../src/main/java/com/playsi/aero_cam_sync/mixins/compat/SimulatedStaffBeamMixin.java),
which routes that call into Sable's eye helper.

### Physics staff, where the beam starts

A different place with a similar symptom, and both fixes are needed. The staff tip is built as
"an offset relative to the camera, plus the player's feet, plus the eye height". That
reconstruction of the camera position is correct only while the camera has not moved. ACS rotates
it around the feet, so the real camera is elsewhere and the tip stays on the vanilla point.

Fix: [`SimulatedStaffFocusMixin`](../src/main/java/com/playsi/aero_cam_sync/mixins/compat/SimulatedStaffFocusMixin.java).
It adds the delta to the result rather than to any of the three terms, because the first term
already accounts for FOV and rotation and the other two must keep vanilla crouch smoothing
intact. It checks first person explicitly, so a mod that turns the tilt on in third person
through the API does not silently get a correction in a branch that measures from the body
instead of the camera.

### Physics staff, where it drags the object to

Server-side. The goal is held as "the player's eye plus the look vector at holding distance", and
the eye is assembled by hand. The relative goal arrives from the client already tilted, since the
look direction is tilted globally, while the point it is added to knows nothing about the tilt.
The object hangs away from where you aim, and the error grows with lean.

Fix: [`SimulatedStaffGoalMixin`](../src/main/java/com/playsi/aero_cam_sync/mixins/compat/SimulatedStaffGoalMixin.java).
The tilt comes from the server-side store, so the arithmetic matches the client's.

---

## Cut Through

**Mod id `cutthrough`.** The most involved entry here, and the only one that needs a library.

Cut Through adds a third candidate to the vanilla pick: its own ray that passes through blocks
without collision, such as grass and campfires. If that ray stops further away than the vanilla
one, it wins. The comparison is a plain distance from the plain vanilla eye.

Half of this is not an ACS bug at all. A sub-level lives in its own coordinate space millions
of blocks from the player. Sable patches everything in the vanilla pick that measures distances,
but its patches are bound to vanilla method names, and Cut Through's handlers live in separate
generated methods those names do not reach. On a sub-level both hits are in sub-level coordinates
while the eye is in world coordinates, so both distances are garbage and which one is larger is
chance. Cut Through works intermittently on sub-levels depending on the angle,
[with ACS absent entirely](troubleshooting.md#problems-that-are-sables-not-acss).

ACS fixes it in two independent halves, because neither is sufficient alone.

- **The origin of its own ray.** Nothing special is needed: the ray goes through `Level#clip` and
  the general net catches it. There used to be a dedicated mixin here, removed once the net
  existed.
- **The metric and the reference point**, which is what the mixin below does. There is no ray
  here, only arithmetic over points that already exist, so the net cannot reach it by
  construction. Measuring from the vanilla eye would compare distances from a point nothing was
  fired from, and at around 35 degrees of lean the offset is about half a block, which is enough
  to flip the choice in a close case.

Fix: [`CutThroughDistanceMixin`](../src/main/java/com/playsi/aero_cam_sync/mixins/compat/CutThroughDistanceMixin.java).

It needs MixinSquared because the handlers are targeted by their original names through
`@TargetHandler`. An ordinary selector cannot reach them at any priority: handlers written with
MixinExtras sugar are inserted during injector application, later than an ordinary injector looks
for targets. Their names in the target class are also generated, and part of the generated name
depends on how many classes have been processed, which means on the mod set.

The released jar carries MixinSquared inside it, so this is normally not something you deal with.
If the library is missing anyway, the mixin is not applied and ACS says so in the log.
Without it the selector is not registered and parsing it fails on startup, which would take the
whole mod down, so
[`BisectMixinPlugin`](../src/main/java/com/playsi/aero_cam_sync/mixins/BisectMixinPlugin.java)
gates it and prints an explanation instead. `require = 0` does not help here, because this is not
"no targets found" but "the selector is invalid".

There is a watchdog, because the price of targeting foreign handler names is that a rename
breaks all four redirects silently. If Cut Through is installed and the redirect body has not run
once after ten seconds in a world,
[`CutThroughCompat`](../src/main/java/com/playsi/aero_cam_sync/client/compat/CutThroughCompat.java)
logs a warning. It sees only that the code runs, not that it computes the right answer, so it is
a signal to go and read the bytecode rather than a replacement for testing. One false positive is
possible and the warning names it: Cut Through installed with its own feature switched off in its
config.

---

## First-person Model

**Mod id `firstperson`, by tr7zw.** Integrated reflectively, with no hard dependency. With the
mod absent every check answers false and nothing is touched.

The mod renders the player's real body in first person through the ordinary player renderer,
shifted behind the camera. The ACS camera rotates around the feet while the body stays upright,
so the head separates from it.

Fix: [`FirstPersonModelTiltMixin`](../src/main/java/com/playsi/aero_cam_sync/mixins/client/FirstPersonModelTiltMixin.java),
which tilts the body by the same smoothed rotation around the same pivot, so the head returns
under the camera and the body reads as standing on a leaning surface. It runs only while the mod
is actually rendering the local player's body in first person, which
[`FirstPersonCompat`](../src/main/java/com/playsi/aero_cam_sync/client/compat/FirstPersonCompat.java)
answers. Other players and third person are untouched.

It applies only when the camera is shifted in position, not when it is only rotated. The shift is
what takes the camera off the eyes and separates the head. With rotation alone the camera stays
on the eyes, and tilting the body would be what separates it.

---

## Mods that need nothing

Not everything that looks like a conflict is one.

**Anything whose aiming ray goes through `level.clip()` from the player's eye** is corrected by
the general net, with no entry on this page. That is most mods.

**Physics from other mods** does not reach the net at all, because it does not ask for the
player's eye. If your probe does start at the eye and is being corrected when it should not be,
that is [a fixable false positive](troubleshooting.md#say-the-ray-is-not-the-players-aim) and it
needs no dependency.

**Background raycasts from other threads** are not touched. The tilt lives on the
client main thread, and ACS does not touch rays cast anywhere else.

---

## Reporting a conflict

The useful report is the log with `Debug messages` on in the ACS config, what the player was
standing on, the camera mode, and whether any mod that drives the tilt through the API was
installed. See [Diagnosing a problem](troubleshooting.md#diagnosing-a-problem).

If you can, narrow the group first. ACS accepts a diagnostic JVM flag that leaves the mod loaded
while withholding part of the mixin set:

```
-Daero_cam_sync.mixins=compat
```

That applies everything except the compatibility mixins on this page. `client` withholds the
client set, `off` withholds all of them. It is a bisect switch and not a way to disable the mod:
with it the config, the network and the API are still live and may behave oddly. To turn the mod
off properly there is the `Enabled` setting.
