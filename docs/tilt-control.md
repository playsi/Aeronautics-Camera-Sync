# Controlling the tilt

For a mod that wants the tilt to be something other than what ACS works out on its own: driving
it, standing it down, or changing what ACS does while a scenario runs.

Read [One frame, one head pose](#one-frame-one-head-pose) first. It is short, and most of the
rules further down follow from it.

---

## Common cases

**"My screen or cutscene must not have a tilting camera."** Use `suppress(millis)`, and remember
the ease-out: check `tiltApplied()`, not `suppressed()`. See
[Asking ACS to stand aside](#asking-acs-to-stand-aside).

**"My mod moves the player's camera itself, and the tilt dies whenever the vanilla hitbox enters
a block."** The ACS collision check measures from the vanilla eye, which is not where your eye
is. State `takeOverCameraCollision(reason)` on the frames it applies to, and read the duty that
comes with it in [Taking over camera collision](#taking-over-camera-collision). Do not reach for
`suppress` here: that turns the tilt off, which is the symptom you are trying to fix.

**"My mod and ACS both raycast the same surface, slightly differently."** Stop correcting the
ACS answer and give it yours. A [tilt source](#driving-the-tilt-yourself) removes the second
computation instead of chasing it. Two mods smoothing one floor by their own clocks agree by
coincidence, and the coincidence breaks at a step between sub-levels.

**"My scenario owns the camera, but my predicate has to stay narrow."** Both, and they do not
conflict: a narrow `appliesTo` plus `skipBaseline` on the frames you decline. Do not widen the
predicate to `true`, which takes the tilt from every player everywhere for the whole session.

**"My source stopped winning and I do not know why."** Someone named you in
[`skip`](#asking-acs-to-stand-aside), or someone above you claimed the frame. Both are in the log
with mod ids, and both are in `FrameReport` under `skipped()` and `tiltSource()`.

**"I held a switch and it silently stopped applying."** There are no switches. A condition exists
for the frame you state it on, and silence means "no". See
[Conditions on a frame](#conditions-on-a-frame). Do not state one from a config flag alone: the
question is whether your scenario is running on this frame, not whether your mod is enabled.

**"I want the tilt in third person."** If you are driving the frame you already have it, because
camera mode does not gate sources. `baselineInThirdPerson` is only for the built-in tilt, on the
frames you do not claim.

---

## One frame, one head pose

One frame is one head pose, and it belongs to one mod. Most of the rules below follow from that.

It is not about the camera. The same value decides the crosshair, the origin and the direction of
every ray that leaves the eye, where thrown items and projectiles start and which way they go, and
how far the player can reach. A copy of it goes to the server, which settles hits with it. Claiming
a frame is not a visual effect, it is deciding where this player's arrows land.

Two mods cannot share a frame, because there is nothing to share. Take half of one rotation and
half of another and you get a head that looks one way and reaches from somewhere else. That is
[the hybrid ray](troubleshooting.md#a-ray-has-two-halves-tilted-by-different-rules): a tilted
direction with an untilted origin, or any pose stitched together from two sources. It hides well,
because the ray still hits *a* block, and it stops hiding the moment precision matters. Every "there
is no way to ask for half of this" below comes from that and nothing else.

When nobody claims the frame, the tilt is the baseline: the built-in behaviour, a raycast of the
surface under the player's feet, the threshold that decides whether it counts, and the smoothing
that follows it. It is a participant and not an off state, and it is not something you can register
underneath.

---

## Three ways to talk to ACS

If your ray goes through `level.clip()`, you need none of them. ACS already moves its origin into
the tilted camera. That covers most mods, and it is why this API is small.

| | what it does | entry point |
|---|---|---|
| **Read** | you ask, ACS answers, nothing changes | `ACS.state(player, partialTick)` |
| **Drive** | you claim frames and own the pose on the ones you win | `ACS.addTiltSource(priority, source)` |
| **Condition** | every frame, you state what ACS may and may not do on it | `ACS.addConditions(conditions)` |

**Read** is all most mods that need ACS at all will ever use: one snapshot per frame, carrying
`vanilla*` and `aim*` pairs, so you can build a ray from where the player is really aiming. See
[Ask for the ray](troubleshooting.md#ask-for-the-ray).

**Drive** is for a mod that works out the tilt itself and wants ACS to stop guessing at the same
floor: a vehicle with its own physics, a dimension with its own gravity, a scenario that owns the
camera outright. See [Driving the tilt yourself](#driving-the-tilt-yourself).

**Condition** is for what a mod needs from ACS while its scenario runs, whether or not it is
driving: stand the built-in tilt down, allow it in third person, take the camera-collision duty
over, skip another mod's source for a frame. See [Conditions on a frame](#conditions-on-a-frame).

`AimPolicy` is a fourth and not one of the three, because it answers about a single *ray* rather
than a frame. It is called from inside `clip`, dozens of times per frame, under a no-allocations
rule. A per-frame callback and a per-ray callback have opposite cost profiles, and merging them
would either permit allocation on a hot path or forbid it where it is harmless. See
[Tell ACS how to treat your ray](troubleshooting.md#tell-acs-how-to-treat-your-ray).

---

## Asking ACS to stand aside

Three levers, from widest to narrowest. Pick the smallest one that covers your scenario.

| what you want | call | scope |
|---|---|---|
| nobody tilts anything, for a while | `ACS.suppress(millis)` | everyone, including the baseline, on a clock |
| the built-in tilt stands down | `conditions.skipBaseline(reason)` | the baseline, this frame |
| one named mod does not drive | `conditions.skip(modId, reason)` | one source, this frame |

### suppress

The cutscene lever, and the only one that runs on a clock.

```java
ACS.suppress(3_000);   // milliseconds, extends your own lease if you already hold one
ACS.release();         // drops yours, other mods' leases are untouched
```

It stops everyone, the baseline and a mod driving the frame alike: while a lease is held, sources
are not asked at all. A cutscene has to be able to take the camera regardless of who is holding it.

It is a lease with an owner rather than a switch, so the mod that finishes first cannot
un-suppress the one still running. The tilt eases out and back rather than snapping, since a
cutscene that begins with the camera jerking is a bug report. The clock is real time but stops
while the game is paused. All leases are dropped when the player leaves the world. A lease longer
than ten seconds logs a warning with your mod id. On a dedicated server it does nothing, because
the tilt is computed client-side, and warns once.

The player holds the same lever. The `Suppress All` option in the ACS config forces suppression on
for diagnosis, and while it is on your source is never asked, exactly as if another mod held a
lease. If your source has gone silent and nothing in your own code explains it, check that first:
`AcsState.suppressed()` is `true` and `suppressedBy()` names `aero_cam_sync:suppress-all` rather
than a mod id.

Do not use it to mean "I will handle the tilt myself". It switches off the machinery you came for,
the ray net and the projectile correction and the packet, and leaves you to write all of it. What
you want is `skipBaseline` plus a [tilt source](#driving-the-tilt-yourself).

### skipBaseline and skip

Both are frame conditions, so they are stated from [`AcsConditions`](#conditions-on-a-frame)
rather than called.

`skipBaseline` stops the ACS floor raycast, threshold and smoothing, and nothing else. Sources
are asked exactly as before, and the winner still gets the camera, the crosshair, every ray, the
projectiles and the sync to the server. On a frame where it is stated and nobody claims, there is
simply no tilt instead of the baseline's.

`skip` names one mod. That mod's source is passed over during resolution as though it had
declined, and the frame goes to whoever is next down the stack. Skips are collected once, before
the stack is walked, so they cannot cascade: two mods skipping each other both lose the frame to
the baseline, which is a defined answer reached in bounded time. ACS does not arbitrate between
third-party mods and will not start.

---

## Taking over camera collision

ACS keeps the camera out of blocks by measuring from the vanilla eye, a point straight up in
world Y from the player's feet. If your mod puts the camera somewhere that point does not
describe, that measurement is meaningless: it lands inside the hull while the real camera is in
open air, ACS concludes the camera is buried, and the tilt scales to zero. This condition is how
you say that you know where the camera is and ACS does not.

```java
@Override
public void conditionsFor(ConditionContext context, FrameConditions conditions) {
    if (myScene.ownsTheCamera(context.player())) {
        conditions.takeOverCameraCollision("we keep our own camera out of blocks");
    }
}
```

**The contract: on a frame where you state this, you guarantee the camera point is not inside a
block.** ACS checks nothing on that frame, and seeing through a wall is then your mod's bug.

Details worth knowing.

- It is a frame answer, not a switch you hold. A switch held for as long as your mod is enabled
  asserts this on every frame the player stands on flat ground, switching the protection off on
  all of them for nothing. The assertion is not about your config, it is about whether your mod is
  placing the camera right now.
- State it from the same predicate the rest of your scenario uses. If the two can disagree, they
  eventually will, and the frame where they do is a camera inside a wall.
- `reason` is mandatory and non-blank, `IllegalArgumentException` otherwise. It is logged once with
  your mod id, which is what makes "why does this camera clip through blocks" answerable from a log
  with no reproduction steps.
- It covers all of camera collision, the same ground the player's `Camera collision` setting
  covers. The clamp is also what keeps the wall scale honest, so with it taken over the tilt is no
  longer reduced near walls, for you or for the baseline.

---

## Third person and the baseline

By default the baseline does nothing in third person. The camera is vanilla, the crosshair is
vanilla, and every ray leaves the eye exactly where vanilla put it. If the built-in tilt belongs
back there for your scenario, say so per frame:

```java
conditions.baselineInThirdPerson("cinematic camera runs in third person");
```

With it stated, third person behaves like first person for the baseline: the camera is rotated
and every ray follows it.

Check whether you need it at all. It conditions the ACS tilt, on the frames nobody claimed. A tilt
source is asked in third person whether or not anybody states this, because camera mode does not
gate sources. If your scenario drives the camera in third person, you need
[a source](#driving-the-tilt-yourself) and nothing else.

There is no way to ask for one half of it, the rays tilted without the camera. The tilt quaternion
is only maintained while it is actually being applied, so those rays would aim along a value nobody
is updating, which is
[the hybrid ray](troubleshooting.md#a-ray-has-two-halves-tilted-by-different-rules) handed back
to you.

Two details. `reason` is mandatory and non-blank, logged once with your mod id. And conditions
are collected before anything about the frame is decided, so a condition never takes effect
halfway through a single pick.

---

## Driving the tilt yourself

A tilt source decides the tilt on the frames it claims. The baseline is what happens on the
frames nobody does.

```java
public final class MyTiltSource implements TiltSource {

    @Override
    public boolean appliesTo(TiltContext context) {
        return myPhysics.isLeaning(context.player());   // narrow, see below
    }

    @Override
    public Quaternionf tilt(TiltContext context) {
        return myPhysics.orientation(context.partialTick());
    }
}

// once, during client setup:
ACS.addTiltSource(100, new MyTiltSource());
```

**What claiming a frame costs you.** Re-read [One frame, one head pose](#one-frame-one-head-pose).
The tilt you return is synced to the server and decides the direction a projectile leaves along,
the point it leaves from, and block and entity reach. Claim a frame you did not need and you have
taken those from the player for no reason.

The predicate must be narrow. A source that answers `true` unconditionally has taken the tilt for
the whole session, and the ordinary ACS surface handling never runs again, including for players
standing on sub-levels your scenario knows nothing about. Answer for the frames your scenario is
actually about.

There is a tension here and it has an answer. A narrow predicate means the ACS floor tilt
reappears on every frame you decline, on top of your scenario. If that is wrong for your mod,
state [`skipBaseline`](#skipbaseline-and-skip) on those frames. Narrow predicate plus "and on the
others, no tilt" is the supported combination. "Claim everything" is not.

Priority: higher is asked first, ties broken by registration order, and the first source to claim
wins the frame whole. The baseline is not in the list, it is what happens when the list runs out,
so any priority outranks it. Keeping it out is on purpose, since a slot at the bottom would be a
slot you could register below, and a source that can never win is a defect nothing would report.

Modifying the ACS tilt rather than replacing it is not a separate mechanism. `context.acsTilt()`
is the answer ACS arrived at before anyone was asked. Take it, transform it, return the result:

```java
@Override
public Quaternionf tilt(TiltContext context) {
    return new Quaternionf().slerp(context.acsTilt(), 0.5f);   // half the lean
}
```

Moving the eye, not only turning it. The ACS tilt rotates the eye about the player's feet. If your
body pivots somewhere else, at the neck or a seat or a hip, that arc is not yours, and the fix is
not to compute the camera position a second time on your side:

```java
@Override
public Vec3 eyeOffset(TiltContext context) {
    Vec3 head = myPhysics.headPosition(context.partialTick());   // where it really is
    return head.subtract(context.cameraPosFor(myTiltThisFrame)); // where ACS would have put it
}
```

A delta, not a position. `cameraPosFor` is the ACS formula, and the offset is measured against it.
"Put the camera here" would make you responsible for everything vanilla does to that point: crouch
smoothing, view bob, and up to four blocks of third-person zoom. Writing that formula down on your
side means there are two copies of it again, which is the failure this API was added to close.

**Reach follows the eye.** The offset moves the origin of every aiming ray, and interaction range
is measured from it, on the server. Push the eye a metre forward and the player reaches a metre
further, through whatever that metre crossed. Above four blocks ACS scales it back to four and
logs a warning. If your scenario means it, say so from `eyeOffsetIsDeliberate`, which is asked
only when the ceiling is actually exceeded.

Smoothing is yours, and so is the ease-out. The tilt resumes gracefully, because the ACS slerp
carries on from whatever value you left behind. A vector ACS does not own has nowhere to resume
from, so the frame after your last claim the offset is zero. A large offset at the moment you stop
claiming is a visible jump.

What overrides you: [`suppress`](#suppress) from any mod, and [`skip`](#skipbaseline-and-skip)
naming yours. Nothing else. Not the player's ACS toggle, not the camera mode, not the
`Rotate camera` or `Shift camera position` settings. Those split the *ACS* tilt into halves of
its own arithmetic. Yours has no such halves, and letting the player switch off half of someone
else's computation is the hybrid ray again.

Costs. `appliesTo` is called once per frame per source, down to the winner. `tilt` and `eyeOffset`
are called on the winner only. A predicate that throws is treated as "declined" and logged,
because your bug should not become a black screen. A source that claims the frame and then returns
`null` from `tilt` passes the frame to the next source, with a complaint in the log.

---

## Conditions on a frame

A tilt source is for driving. Conditions are for everything you need ACS to do differently while
your scenario runs, whether or not you are driving.

```java
public final class MyConditions implements AcsConditions {

    @Override
    public void conditionsFor(ConditionContext context, FrameConditions conditions) {
        if (!myScene.isRunning()) return;                 // silence means "no"
        conditions.skipBaseline("our scene owns the camera here");
        conditions.takeOverCameraCollision("we place the camera ourselves");
    }

    @Override
    public void frameResolved(FrameReport report) {
        myDebugOverlay.record(report.tiltSource(), report.tiltScale(), report.skipped());
    }
}

// once, during client setup:
ACS.addConditions(new MyConditions());
```

Four conditions, all frame-scoped:

| | means |
|---|---|
| `skipBaseline(reason)` | do not apply the ACS tilt on this frame |
| `skip(modId, reason)` | do not ask that mod's source on this frame |
| `baselineInThirdPerson(reason)` | let the ACS tilt run in third person on this frame |
| `takeOverCameraCollision(reason)` | on this frame, keeping the camera out of blocks is your job |

**A condition is an answer, not a switch. Silence means "no".**

There is nothing to unset. A condition exists for exactly the frame you state it on, with no
residue from the last one, ever. Forgetting to state it is the same as turning it off.

The cost of that is real: your predicate has to be right every frame rather than once at startup.
What it buys is that a mod cannot assert something about the world for hours after it stopped
being true, which is what the switches this replaced actually did.

Two phases, and neither can do the other's job.

`conditionsFor` runs *before* ACS resolves who drives the frame, because its answers are inputs
to that decision: which sources may be asked at all, and whether the ACS tilt is allowed to be
the answer. So there is no surface normal and no `acsTilt` in `ConditionContext`. That ray has
not been cast yet, and whether it gets cast is partly what you are answering. If you need those
values, you are describing a source rather than a condition.

`frameResolved` runs after everything is applied and reports who won, the pose actually used,
`tiltScale`, and who was skipped. It answers "a lot can happen in one frame and I want to know
what": a source can take it, a skip can pass that source over, and the wall clamp can scale the
result to nothing, all leaving the same final quaternion behind. Do not keep the `FrameReport`,
it is valid for the call only.

How skips resolve. Every registered mod is asked for its conditions once, before resolution
begins. The skips they name are collected into one set, then the stack is walked from the highest
priority down, passing over anyone in it. Collection does not repeat, which is what makes the
outcome defined and bounded.

One object may implement both `TiltSource` and `AcsConditions`, and that is the recommended shape
when your mod does both. Register it with both calls. The gain is not brevity: "is my scenario
running right now" gets answered once, and two answers that could drift apart do not exist.

```java
MySource source = new MySource();
ACS.addTiltSource(MySource.PRIORITY, source);
ACS.addConditions(source);
```

`conditionsFor` is called on your own frames too, including the ones you go on to win, which is
where taking over camera collision matters most.

Costs. Only registered mods are called, once per frame each, in registration order. Do not raycast
in here and do not allocate: these are cheap booleans about a scenario whose state you already
know. A method that throws is logged, the frame carries on, and anything you had already stated on
it stands, because ACS does not roll half a frame back.
