# ZwoTeleport

Teleports, TPA, warps and spawn-on-join for **Paper 1.21.11** servers.

- Admin `/tp`, `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpacancel`
- Warps, `/spawn`, `/setspawn`, `/loja` and warp shortcuts as commands
- Warmup, cooldown and combat blocking
- Native spawn on join (replaces Skript/EssentialsXSpawn for this feature)
- Optional safe destination, loaded asynchronously
- API and events for other plugins

Requirements: Paper 1.21.11 and Java 21. Folia is not supported.

Player-facing messages and configuration comments are in Brazilian Portuguese.

## Installation

1. Stop the server.
2. Put `ZwoTeleport-3.0.0.jar` in `plugins/` and remove the old ZwoTeleport JAR.
3. Start the server. On first start the plugin creates `config.yml` and `messages.yml`
   (or updates the existing ones, see below).
4. Set the spawn with `/setspawn` at the desired position and facing direction, if it does not exist yet.
5. Adjust the configuration and run `/zwotp reload`. Check the state with `/zwotp info`.

## Upgrading from 2.x

The upgrade is automatic and keeps your data:

- Before changing any file, the plugin copies `config.yml`, `messages.yml` and `warps.yml`
  to `plugins/ZwoTeleport/backups/pre-3.0.0-<date>/`. This happens only once.
- `warps.yml` is not modified: the format is the same as in 2.x. World, coordinates, yaw and
  pitch saved by the old `/setspawn` keep working.
- `config.yml` and `messages.yml` keep all of their current content. New options and messages
  are **appended to the end** of the file; new sections come with their explanatory comments.
- The old alternative keys (`teleport.warmup-seconds`, `teleport.cooldown-seconds`, etc.) are
  still accepted, with a console warning.

### Remove the spawn Skript

Spawn on join ships **enabled** with `join-spawn.mode: EVERY_JOIN`, as planned for New Era.
If the server has a Skript (or EssentialsXSpawn) that sends players to spawn on join,
**remove that script** before starting 3.0.0; otherwise players will be teleported twice.
To keep the Skript for now, set `join-spawn.mode: DISABLED`.

## Spawn on join

| Mode | Behavior |
|---|---|
| `DISABLED` | Never sends the player to spawn on join. |
| `FIRST_JOIN` | Only on the player's first join to this server (no saved data in the world). |
| `EVERY_JOIN` | On every join to this server. |

- Uses the warp set in `join-spawn.warp` (default `spawn`, the same one used by `/setspawn`),
  with the exact world, position, yaw and pitch.
- Does not run `/spawn`, applies no warmup, consumes no cooldown and grants no permissions.
- The teleport happens `join-spawn.delay-ticks` after joining (default: 1 tick) and is bound to
  that connection: if the player leaves and rejoins before it runs, the old task does nothing.
- Missing spawn or unavailable world: the player stays where they joined and the console gets a
  warning (at most one per minute).
- `zwo.joinspawn.bypass` skips spawn on join. **Nobody has this permission by default, not even
  OP**; grant it explicitly to whoever needs it (e.g. builders).
- Players who quit while combat-tagged stay where they were on their next join
  (`join-spawn.skip-if-quit-in-combat`), so leaving and coming back cannot be used to escape.
- Death, bed, respawn anchor and respawn rules are not changed.

**Limitation:** the server does not know where the player came from. With `EVERY_JOIN`, joining
from the Hub, returning from another instance and reconnecting are handled the same way.
Telling apart a specific Hub → Survival transfer would require validated communication with
Velocity, which is not part of this version.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/tp <player>` | `zwo.tp.admin` | Teleport yourself to the player. |
| `/tp <x> <y> <z> [yaw pitch]` | `zwo.tp.admin` | Teleport yourself to coordinates (`~` supported). |
| `/tp <player> <target>` | `zwo.tp.admin` | Teleport a player to the target. |
| `/tp <player> <x> <y> <z> [yaw pitch]` | `zwo.tp.admin` | Teleport a player to coordinates. |
| `/tpa <player>` | `zwo.tpa` | Ask to teleport to the player. |
| `/tpahere <player>` | `zwo.tpahere` | Ask the player to teleport to you. |
| `/tpaccept [player]` (`/tpaceitar`) | `zwo.tpa.respond` | Accept a request (no name: the most recent). |
| `/tpdeny [player]` (`/tpnegar`) | `zwo.tpa.respond` | Deny a request. |
| `/tpacancel` (`/tpcancelar`) | `zwo.tpa` | Cancel the requests you sent. |
| `/warp [name]` | `zwo.warp.use` | Go to a warp; without a name, list the available ones. |
| `/warps` | `zwo.warp.use` | List the available warps. |
| `/setwarp <name> [display name]` | `zwo.warp.admin` | Create or update a warp at your position. |
| `/delwarp <name>` | `zwo.warp.admin` | Delete a warp. |
| `/spawn` | `zwo.warp.use` | Go to spawn (warp rules). |
| `/spawn <player>` | `zwo.spawn.others` | Send another player to spawn, without warmup. |
| `/setspawn` | `zwo.warp.admin` | Set the spawn, including the facing direction. |
| `/loja` | `zwo.warp.use` | Go to the `loja` warp. |
| `/zwotp reload\|info\|combaton\|combatoff` | `zwo.zwotp.admin` | Administration. |

Every command is also available with a namespace, e.g. `/zwoteleport:tpa`, for diagnostics and
for when another plugin uses the same name.

### Precedence when commands conflict

1. ZwoTeleport loads before Essentials, CMI and similar plugins (`loadbefore`), so it gets the
   plain names of the commands in its `plugin.yml`. The other plugin's commands stay available
   through its own namespace (e.g. `/essentials:warp`).
2. `commands.takeover` lists aliases that ZwoTeleport takes over even if another plugin
   registered them (default: the TPA aliases). Every takeover is logged to the console, and the
   previous command is restored when the alias is removed from the list or the plugin is
   disabled. No reflection is used.
3. Warp shortcuts (`/minas`) only work when **no** plugin registered a command with that name;
   ZwoTeleport never overrides other plugins' commands with a shortcut.
4. Commands cancelled by another plugin (CommandWhitelist, mutes, events) are respected.

## Permissions

| Permission | Default | Effect |
|---|---|---|
| `zwo.admin` | op | Includes `zwo.tp.admin`, `zwo.warp.admin`, `zwo.zwotp.admin`, `zwo.spawn.others` and `zwo.tpa.bypass.restrictions`. Bypasses cooldown and combat. |
| `zwo.tp.admin` | op | `/tp`. Bypasses combat and the `/tpahere` cooldown. |
| `zwo.warp.admin` | op | Create/delete warps and the spawn; access to every warp; bypasses cooldown and combat. |
| `zwo.tpa`, `zwo.tpahere`, `zwo.tpa.respond`, `zwo.warp.use` | everyone | Regular use. |
| `zwo.warp.<name>` | — | Required per warp when `warps.require-per-warp-permission: true`. |
| `zwo.bypass.cooldown` | op | Bypasses cooldown if `teleport.cooldown.allow-bypass-permission: true`. |
| `zwo.tpahere.bypass.cooldown` | op | Bypasses cooldown only when sending `/tpahere`. |
| `zwo.bypass.warmup` | op | Bypasses warmup if `teleport.warmup.allow-bypass-permission: true`. |
| `zwo.bypass.combat` | op | Never tagged or blocked by the built-in combat system. |
| `zwo.tpa.bypass.restrictions` | op | Ignores `tpa.blocked-permissions` and `tpa.blocked-worlds`. |
| `zwo.joinspawn.bypass` | **nobody** | Not sent to spawn on join. |
| `zwo.zwotp.admin` | op | `/zwotp`. |

Being OP grants nothing outside these rules: an OP with a permission negated in the permissions
plugin loses that effect.

## Warmup, cooldown and combat

- **Warmup:** the player waits `teleport.warmup.seconds`. Turning the camera never cancels;
  walking beyond `max-horizontal-distance` or moving up/down beyond `max-vertical-distance`
  cancels. Taking damage, entering combat, being teleported by another plugin/portal and leaving
  the server also cancel. When the warmup ends, everything is checked again (world, coordinates,
  combat, other plugins).
- **Cooldown:** warps, `/spawn` and shortcuts check the cooldown when the command is used and
  record it **only when the teleport completes successfully**; cancelled or failed teleports do
  not consume it. `/tpa` and `/tpahere` check and record it when the request is **sent**, to
  prevent spam.
- **Combat:** player-versus-player damage (including arrows, potions and TNT from another player)
  tags both attacker and victim for `teleport.combat.tag-seconds`. Damage cancelled by region
  protection does not tag. The tag still applies if the player leaves and rejoins before it expires.
- `/tp`, `/spawn <player>` and spawn on join have no warmup, cooldown or combat blocking.

## Network (Velocity, Hub, Survival, Events)

- ZwoTeleport only handles teleports **within this server**. `/tpa` never sends a player to
  another server, and no feature of the plugin sends players to the Events server.
- There is no cross-server messaging in this version: nothing to authenticate, nothing a client
  could forge. Authentication stays with nLogin on the proxy; inventories and UUIDs are untouched.
- Transfer plugins (Hub, Events) can query the API (`isTeleportBlockedByCombat`) to deny leaving
  while a player is in combat.

## Safe destination

`safety.<type>` sets the policy per teleport type: `NONE` (exact position), `VERIFY` (refuse if
unsafe) or `ADJUST` (find the nearest safe position). Defaults: `/tpa` uses `ADJUST`; warps,
spawn and `/tp` use `NONE` so positions set by staff are never altered. The destination chunk is
loaded asynchronously and the search runs off the main thread, limited to that chunk and the
configured ranges.

## Storage

Warps are stored in `warps.yml`. Writes are atomic (temporary file + swap), the previous version
is kept in `warps.yml.bak`, and a corrupted file is copied to `warps.yml.corrompido-<date>`
before the plugin falls back to the backup. Without a readable backup, writes are blocked so the
file is never overwritten. Teleport requests and warmups are never persisted.

YAML is the only storage in this version. The `WarpRepository` layer is ready for a future
MariaDB/MySQL implementation (see `docs/ARQUITETURA.md`).

## API

```java
ZwoTeleportApi.find().ifPresent(api -> {
    api.teleportToWarp(player, "loja", TeleportOptions.likePlayerCommand())
        .thenAccept(result -> {
            if (result.completed()) {
                // the player is already at the shop
            }
        });
});
```

Details, events and guarantees in [`docs/API.md`](docs/API.md).

## Building

```bash
./gradlew build
```

The JAR is written to `build/libs/ZwoTeleport-3.0.0.jar`. Tests run as part of `build`.

## Documentation

- [`CHANGELOG.md`](CHANGELOG.md) — changes in 3.0.0
- [`docs/AUDITORIA.md`](docs/AUDITORIA.md) — confirmed bugs in 2.0.4 and improvements
- [`docs/API.md`](docs/API.md) — API and events
- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) — code layout and persistence
- [`docs/TESTES.md`](docs/TESTES.md) — tests performed and limitations

The documents in `docs/` are in Brazilian Portuguese.
