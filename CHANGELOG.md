# Changelog

## 1.4.0

### Added
- Added a public API for other mods, and a documentation. See [API](https://github.com/playsi/Aeronautics_Camera_Sync/blob/master/docs/API.md).
- Added tilt sources: another mod can decide the tilt itself and take the frame from ACS on the frames its scenario is actually about via API.
- Added a `Suppress All` option. It forces the tilt off completely, including a tilt another mod is driving through the API. Use it to check whether the tilt is behind a problem at all.

### Changed
- Reworked how aiming is corrected. Rays now start from the tilted camera instead of the result being fixed up afterwards.
- The tilt now comes from the sub-level's own orientation instead of an average of downward rays.
- `Allow in 3rd person (Beta)` is gone from the settings. Third person is now available only from the API.
- `Instantly forget Sublevel` is gone from the settings. Mod now always forgets a sub-level the moment the player steps off it.
- `create:handheld_worldshaper` is no longer blacklisted by default.
- `Enable Mod` and the tilt switch now only block ACS's own tilt; the API continues working.

### Fixed
- Finally fixed interact with throttle lever by its rod. (Issue #2) 😭
- Fixed camera shaking when the player is standing on 2 or more sub-levels.
- Fixed a bug that caused the slope to disappear on sub-levels smaller than a single block. (Issue #32)
- Fixed wrong tilt next to a wall or a block at the player's feet on a steeply banked sub-level.
- Fixed incorrect aiming for the Create zapper and worldshaper, rail outlines, honey glue, super glue, and the Create Simulated physics staff.
- Fixed aiming and reach in third person.
- Fixed the tilt being cancelled in third person whenever the camera came near a wall. The camera is still kept out of blocks.
- Fixed entering a vehicle snapping the tilt to level instead of easing to it.
- Fixed a keybind change not surviving a restart.
- Fixed Bits 'n' Tracks visual bug. (Issue #30)

### Note for mod developers
- The API covers tilt sources, per-frame conditions, an eye offset for a body that pivots somewhere other than the feet, and a report of what each frame resolved to. See [API](https://github.com/playsi/Aeronautics_Camera_Sync/blob/master/docs/API.md).
- Two diagnostic launch flags stay in release builds: `-Daero_cam_sync.debug=true` turns the diagnostics, and `-Daero_cam_sync.mixins=off|client|compat` drops the mixin set or one group of it.

## 1.3.6

### Fixed
- Fixed wrong block/entity picking while standing on a tilted contraption (1.3.5 regression)
- Fixed camera tilt jitter while a screen is open, e.g. the Create Simulated diagram (1.3.5 regression)
- Fixed Cut Through compatibility without letting entities be hit through deck blocks again (Issue #16, Issue #26)
- Fixed server-side features silently doing nothing on dedicated servers: the handshake never completed, so projectiles, buckets, tossed items and aim were left uncorrected (Issue #33)
- Fixed aim desync when Client Side Only was toggled during play: the client fell back to client-only mode while the server kept aiming by the last tilt it had received

### Reworked
- Client Side Only now applies after rejoining the world; while the change is pending, the mode badge in the config screen is greyed out and says so

## 1.3.5

### Added
- Added server-side tilt sync: projectiles, tossed items, buckets and aim follow the tilted camera when the mod is installed on the server (by MrLemonHog, #28)
- Added activation thresholds: tilt only on contraptions above a minimum mass, block count or size
- Added camera collision with a smoothing slider
- Added auto-disable for projectile and bucket items in client-only mode
- Added mode indicator to the config screen (`mode: client-only` / `mode: server-client`)
- Added default blacklisted items (`create:handheld_worldshaper`, `create:potato_cannon`)

### Fixed
- Fixed incorrect projectile aiming on rotated contraptions (Issue #3)
- Fixed camera X-ray when tilted near a wall (Issue #27)
- Fixed hitting entities through walls (Issue #26)
- Fixed incompatibility with Sound Physics Perfected (Issue #24)
- Fixed wrong player rendering with First Person Model (Issue #14)
- Fixed incompatibility with Point Blank (Issue #11)
- Fixed wrong fluid placement position when using buckets on a tilted contraption
- Fixed camera collision logic and made it smooth

### Reworked
- Reworked the config screen layout: Camera tab split into Tilt and \Collision sections, options regrouped across tabs
- Options that require the mod on the server are now marked in the config
- Renamed Check Offhand to Include Offhand, rewrote tooltips
- Removed unused server blacklist config entries
- Updated dependencies: Sable 2.0.3, Create Aeronautics 1.3.0, NeoForge 21.1.229

## 1.3.1

### Fixed
- Fixed visual issues with tires & laser pointers (Issue #22)
