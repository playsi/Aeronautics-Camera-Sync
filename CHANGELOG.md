# Changelog

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
