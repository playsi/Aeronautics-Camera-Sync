# Aeronautics Camera Sync
Adds dynamic camera rotation synced with moving contraptions from [Create Aeronautics](https://modrinth.com/mod/create-aeronautics)

## Preview

![comparison](https://github.com/playsi/Aeronautics-Camera-Sync/blob/master/promo/on_the_airship.gif?raw=true)

![comparison](https://github.com/playsi/Aeronautics-Camera-Sync/blob/master/promo/off_the_airship.gif?raw=true)

## Description
**Aeronautics Camera Sync** enhances immersion by synchronizing the player’s camera with the rotation of moving contraptions.

When standing on ships or other dynamic structures from **Create Aeronautics (Sable)**, your view will smoothly rotate along with the contraption instead of staying fixed to the world. This makes movement feel more natural, especially during turns, tilts, and aerial maneuvers.

With the mod installed on the server, the tilt will work correct with projectiles, tossed items and buckets.

## Problems
Some modded items don't work properly with camera tilt and cause wrong throw arcs,
broken interactions, or even crashes.

Fixes:
- **Toggle the tilt off** - press <kbd>I</kbd> (rebindable in the config)
- **Blacklist the item** - add its ID (e.g. `create:potato_cannon`) to the **Item Blacklist** tab in the config screen

## ⚠️ **Requires [Sable](https://modrinth.com/mod/sable)**

## Client / Server behavior

| When                                             | What it means                                                                                                                  |
|--------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| Mod on **client + server** (or singleplayer)     | Full sync: the camera tilts locally *and* the server applies the real tilt to your look/throw direction.                        |
| Mod on **client only** (not installed on server) | Visual tilt only. While holding throwable items (snowballs, bows, buckets, etc.) the tilt is temporarily disabled so they still fly straight. |

The config screen shows your current mode as a badge in the top-right corner: `mode: server-client` or `mode: client-only`.

> A **server-only** install has no effect - players without the mod are unaffected.

#### Special thanks to [mrlemonhog](https://modrinth.com/user/mrlemonhog) for contributing to the project.