# API do ZwoTeleport

Pacote `br.com.zwo.zwoteleport.api`. Declare no `plugin.yml` do seu plugin:

```yaml
softdepend: [ZwoTeleport]   # ou depend
```

e compile contra o JAR do ZwoTeleport como `compileOnly`.

## Regras gerais

- Use a API **somente na thread principal**. Fora dela os métodos lançam `IllegalStateException`.
- Os `CompletableFuture` devolvidos são concluídos na thread principal.
- Um teleporte novo para o mesmo jogador cancela o anterior (motivo `REPLACED`).
- A API não envia jogadores para outros servidores.

## Obtendo a API

```java
Optional<ZwoTeleportApi> api = ZwoTeleportApi.find();
```

Equivale a `Bukkit.getServicesManager().load(ZwoTeleportApi.class)`.

## Consultas

| Método | Descrição |
|---|---|
| `warp(nome)` / `warps()` / `spawn()` | Warps carregadas (`WarpInfo`), com `available` indicando se o mundo está carregado. |
| `canUseWarp(player, nome)` | Mesma regra de permissão do `/warp`. |
| `pendingTeleport(uuid)` | Teleporte em andamento (`PendingTeleportInfo`). |
| `incomingRequests(uuid)` / `outgoingRequests(uuid)` | Pedidos de `/tpa` e `/tpahere` válidos. |
| `isCombatTagged(uuid)` / `combatSecondsLeft(uuid)` | Tag de combate do ZwoTeleport. |
| `isTeleportBlockedByCombat(player)` | Se o combate impediria o jogador de teleportar agora (considera bypass e configuração). |
| `cooldownSecondsLeft(uuid)` | Cooldown restante. |

## Teleportando

```java
TeleportOptions options = TeleportOptions.plugin();        // imediato, silencioso, verifica combate
TeleportOptions comoJogador = TeleportOptions.likePlayerCommand(); // warmup, cooldown, combate e mensagens

api.teleport(player, location, options).thenAccept(result -> {
    switch (result.status()) {
        case COMPLETED -> { /* jogador já está no destino: result.finalLocation() */ }
        case BLOCKED -> { /* cooldown, combate ou outro plugin recusou antes de começar */ }
        case CANCELLED -> { /* movimento, dano, saída, substituição... */ }
        case FAILED -> { /* destino inválido/inseguro, mundo indisponível, servidor recusou */ }
    }
});
```

`TeleportOptions` (record): `cause`, `warmup`, `checkCombat`, `applyCooldown`, `safety`
(`null` usa `safety.plugin` do config), `notifyPlayer`. Métodos `withWarmup`, `withSafety`,
`withNotify` criam variações.

`teleportToWarp(player, nome, options)` não verifica a permissão da warp; chame
`canUseWarp` antes se o teleporte for em nome do jogador.

`cancelTeleport(player)` cancela um teleporte em warmup ou em validação. Depois que o servidor
já recebeu o pedido de movimento (`teleportAsync`), não há como desfazer e o método devolve `false`.

## Eventos

Todos são síncronos (thread principal) e estendem `ZwoTeleportEvent`, que expõe `getPlayer()`,
`getCause()` (`TeleportCause`) e `getTeleportId()`.

Ciclo de um teleporte que passa das verificações iniciais (cooldown, combate, destino válido):

1. `ZwoPreTeleportEvent` com `getPhase() == START` — cancelável.
2. Warmup, se houver.
3. `ZwoPreTeleportEvent` com `getPhase() == EXECUTE` — cancelável, imediatamente antes de mover.
4. Exatamente **um** destes:
   - `ZwoTeleportCompletedEvent` — o servidor confirmou o teleporte; o jogador já está no destino.
   - `ZwoTeleportCancelledEvent` — interrompido; `getReason()` diz por quê. O jogador não foi movido por este teleporte.
   - `ZwoTeleportFailedEvent` — não pôde ser executado; `getReason()` diz por quê.

Recusas antes do passo 1 (cooldown, combate, destino inválido) não disparam eventos; aparecem
apenas no resultado. Durante o desligamento do servidor nenhum evento é disparado; os futures
pendentes terminam com `SHUTDOWN`.

### Bloqueando teleportes (eventos, regiões, minigames)

```java
@EventHandler
public void aoTeleportar(ZwoPreTeleportEvent event) {
    if (estaNoEvento(event.getPlayer())) {
        event.setCancelled(true);
        event.setCancelMessage(Component.text("Você não pode teleportar durante o evento.", NamedTextColor.RED));
    }
}
```

Cancelar na fase `EXECUTE` cobre o caso de a condição surgir durante o warmup.

## Causas (`TeleportCause`)

| Causa | Origem | Warmup | Cooldown | Combate | Política de segurança |
|---|---|---|---|---|---|
| `ADMIN_COMMAND` | `/tp` | não | não | não | `safety.admin` |
| `TPA` / `TPAHERE` | pedido aceito | sim | no envio | sim | `safety.tpa` |
| `WARP` | `/warp`, `/loja`, atalhos | `warps.use-warmup` | sim | sim | `safety.warp` |
| `SPAWN` | `/spawn` | `warps.use-warmup` | sim | sim | `safety.spawn` |
| `SPAWN_OTHER` | `/spawn <jogador>` | não | não | não | `safety.spawn` |
| `JOIN_SPAWN` | entrada no servidor | não | não | não | `safety.join-spawn` |
| `PLUGIN` | API | conforme `TeleportOptions` | | | `safety.plugin` ou a indicada |

## Motivos (`TeleportResult.Reason`)

`COMPLETED`, `COOLDOWN`, `COMBAT_TAGGED`, `CANCELLED_BY_PLUGIN`, `MOVED`, `DAMAGED`, `COMBAT`,
`EXTERNAL_TELEPORT`, `REPLACED`, `QUIT`, `ANCHOR_OFFLINE`, `CANCELLED_BY_API`, `SHUTDOWN`,
`PLAYER_OFFLINE`, `PLAYER_DEAD`, `INVALID_DESTINATION`, `WORLD_UNAVAILABLE`, `OUTSIDE_LIMITS`,
`RESTRICTED_DESTINATION`, `UNSAFE_DESTINATION`, `CHUNK_LOAD_FAILED`, `TELEPORT_REJECTED`.
