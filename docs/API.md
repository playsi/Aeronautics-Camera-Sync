# Aeronautics Camera Sync documentation

ACS tilts the player's camera to match the surface of the Sable sub-level (usually a Create
Aeronautics contraption) they are standing on.

## Topics

**Something in my mod broke.** See [troubleshooting.md](troubleshooting.md).

The player aims at the wrong place, a projectile leaves with an offset, an interaction that
should land does not, anything that behaves incorrectly while the player's camera
is turned. Diagnosis, the cases we see most often, and a fix for each.  
**For most of these, no API is required at all.**

**I want to control the tilt via API.** See **[tilt-control.md](tilt-control.md)**

Use your mod to freely rotate and move the camera, set up custom scenarios, and more.

**Quick start with the API.** See **[reference.md](reference.md)**

Adding the dependency, the first call, and signatures.

**Is my mod already handled?** See **[compatibility.md](compatibility.md)**

Mods ACS carries a specific fix for: what was wrong in each case, and where the fix lives.

---

## Quick ACS diagnostic

The shape of the bug: a game mechanic behaves incorrectly while the player's camera is tilted,
and the further they are tilted, the more pronounced it gets. On flat ground it is gone.

To test, turn the mod off, with the hotkey (`I` by default) or from the config screen. If the
symptom goes with it, it is ours.

If the tilt itself does not stop when the mod is switched off, another mod is driving it through
our API, and a player's toggle does not end someone else's scenario.

`Suppress All`, in the config, is the answer to that. It forces the tilt off for everyone,
including a mod driving it through the API. If the symptom survives it, no tilt of any origin is
causing it.

To rule out ACS rather than the tilt, take the jar out of the mods folder. `Suppress All`
silences the *tilt*, not the *mod*: while ACS is loaded its mixins are woven either way.

If the problem is still there with the jar gone, it is not ACS.
