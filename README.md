# Aeronautics Camera Sync

Rotates your camera together with the moving contraptions from
[Create Aeronautics](https://modrinth.com/mod/create-aeronautics), so standing on an airship
feels really dynamic.

## Preview

![comparison](https://github.com/playsi/Aeronautics-Camera-Sync/blob/master/promo/on_the_airship.gif?raw=true)

![comparison](https://github.com/playsi/Aeronautics-Camera-Sync/blob/master/promo/off_the_airship.gif?raw=true)

## ⚠️ **Requires [Sable](https://modrinth.com/mod/sable)**

## What it does

When you stand on a ship or any other moving sable structure, your view rotates with it instead of
staying locked to the world. Turns, banking and climbs read the way they should, and the
horizon behaves like something you are travelling on rather than something you are watching.

Aiming follows the camera, not just the picture. Where you point is where you hit, where you
throw from is where the item leaves, and how far you reach is measured from where you actually
are.

## Client and server

| Installed | What you get |
|---|---|
| **Client and server**, or singleplayer | Full sync. The camera tilts locally and the server applies the same tilt to your look and throw direction. |
| **Client only** | Visual tilt. While you hold a throwable or a bucket the tilt switches off for as long as you hold it, so the item still flies straight. |
| **Server only** | Nothing. Players without the mod are unaffected. |

The config screen shows the current mode as a badge in the top-right corner: `mode: server-client`
or `mode: client-only`.

## If something breaks

Occasionally an item or a mechanic from another mod behaves incorrectly: a throw that curves
somewhere unintended, an interaction that will not land, a crash while aiming. It comes from how
the game itself is built, and each case needs its own compatibility fix.

Three ways out, from narrowest to widest.

- **Blacklist the one item.** Hold it and press the `Toggle Blacklist Item Key`, or add its id
  (for example `create:potato_cannon`) on the **Item Blacklist** tab of the config screen. The
  tilt turns off only while that item is in your hand. Off-hand items count too.
- **Turn the tilt off.** Press <kbd>I</kbd>, rebindable in the config as `Toggle Camera Tilt`.
- **Turn the mod off.** The `Enable Mod` setting.

To check whether ACS is the cause, run [the quick diagnostic](docs/API.md#quick-acs-diagnostic).
The fixes are in [troubleshooting.md](docs/troubleshooting.md).

## For mod developers

ACS moves the point the player looks *from*. Most mods need no changes: any ray that goes through
`level.clip()` is corrected automatically.

If your mod aims at the wrong place on a tilting contraption, or you want it to drive the tilt
itself, start at the **[documentation index](docs/API.md)**.

- **[Adding the dependency](docs/reference.md#adding-the-dependency).** The repository, the
  coordinates, and the first call.
- **[Driving the tilt yourself](docs/tilt-control.md).** Set your own camera tilt through the
  public API.
- **[Compatibility](docs/compatibility.md).** Which mods ACS carries a fix for, and how each
  incompatibility is resolved.

Free to use in any modpack, public or private. No permission needed, and a link back is welcome
but not required.

#### Special thanks to [mrlemonhog](https://modrinth.com/user/mrlemonhog) for contributing to the project.
