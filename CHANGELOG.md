# Changelog

## 3.0.0

Full rewrite on top of 2.0.4, keeping commands, aliases, permissions, messages and
the `warps.yml` format. Details of the fixed bugs in `docs/AUDITORIA.md`.

### Added

- **Native spawn on join** (`join-spawn`): `DISABLED`, `FIRST_JOIN` and `EVERY_JOIN` (default)
  modes, with no warmup, no cooldown and without running `/spawn`; bound to the player's
  connection, with an explicit `zwo.joinspawn.bypass` permission and combat-escape protection.
- **Central teleport service:** validation of world, finite coordinates, height limits and world
  border; re-validation when the warmup ends; success announced only after the server confirms.
- **Safe destination** per teleport type (`safety`): `NONE`, `VERIFY` or `ADJUST`, with the chunk
  loaded asynchronously and the search running off the main thread.
- **API and events** (`ZwoTeleportApi`, `ZwoPreTeleportEvent`, `ZwoTeleportCompletedEvent`,
  `ZwoTeleportCancelledEvent`, `ZwoTeleportFailedEvent`).
- `/zwotp reload` with full validation (an invalid configuration is rejected and the previous one
  is kept) and `/zwotp info` for diagnostics.
- Configurable `/tpa` restrictions: `tpa.blocked-permissions` and `tpa.blocked-worlds`.
- Configurable movement tolerance during warmup; countdown in the action bar.
- Messages in chat, action bar or title (`[actionbar]`, `[title]`), hex colors `&#RRGGBB`, and a
  line with the commands written out for Bedrock players who cannot click.
- Automatic migration from 2.x with backup, without rewriting the user's files.

### Fixed

- Warmup that kept running after the player left and teleported them on reconnect.
- TPA requests that stayed active after a player left.
- `/tp` with `NaN`/`Infinity` causing an internal error.
- "Teleported X" message from `/tp` and `/spawn <player>` sent before the teleport finished.
- Cooldown consumed by cancelled or failed teleports.
- `/tpa` running even when another plugin (e.g. CommandWhitelist) cancelled the command.
- `/setwarp` over an existing warp erased its specific permission.
- Non-atomic writes to `warps.yml`; now with a temporary file, `.bak` and recovery.
- Warps in worlds loaded after startup were unavailable.
- Player and warp names could change message formatting.
- `/tpa` with part of a name could send the request to the wrong player.

### Changed

- **Taking over other plugins' aliases** is now configurable (`commands.takeover`), uses only
  Paper's public API, logs every takeover and gives the command back on disable.
- The hard-coded block for the nickname "Yarchuww" was replaced by `tpa.blocked-permissions`
  (default: `serpens.yoff.active`, same message). It now applies to any player with that
  permission instead of a name.
- Player lookup accepts the exact name or a unique prefix; partial matches in the middle of a
  name are no longer accepted.
- `/zwotp` requires `zwo.zwotp.admin` (included in `zwo.admin`). Previously `zwo.tp.admin` could
  also toggle combat.
- `/zwotp combaton/off` only changes `teleport.combat.enabled`.
- `zwo.admin` now includes `zwo.tp.admin`, `zwo.warp.admin`, `zwo.zwotp.admin`,
  `zwo.spawn.others` and `zwo.tpa.bypass.restrictions`.
- Bypasses based only on being OP (`isOp()`) were removed; OP still has every `op`-default permission.
- `/tpa` takes the player to the target's current position when the warmup ends (previously: the
  position at the moment of accepting).
- `zwoteleport:*` suggestions remain visible to players with `zwo.admin`.
- `api-version: 1.21`; requires Paper 1.21.11 and Java 21.

### Removed

- Interception of `/tpa` through `PlayerCommandPreprocessEvent` and all use of reflection.
- Automatic removal of a message prefix containing "zwotp"/"zwoteleport".
