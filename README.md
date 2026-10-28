# ZwoTeleport

Teleportes, TPA, warps e spawn na entrada para servidores **Paper 1.21.11**.

- `/tp` administrativo, `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpacancel`
- Warps, `/spawn`, `/setspawn`, `/loja` e atalhos de warp como comando
- Warmup, cooldown e bloqueio por combate
- Spawn nativo na entrada (substitui Skript/EssentialsXSpawn para essa função)
- Destino seguro opcional, carregado de forma assíncrona
- API e eventos para outros plugins

Requisitos: Paper 1.21.11 e Java 21. Folia não é suportado.

## Instalação

1. Pare o servidor.
2. Coloque `ZwoTeleport-3.0.0.jar` em `plugins/` e remova o JAR antigo do ZwoTeleport.
3. Inicie o servidor. Na primeira inicialização o plugin cria `config.yml` e `messages.yml`
   (ou atualiza os existentes, veja abaixo).
4. Defina o spawn com `/setspawn` na posição e direção desejadas, se ainda não existir.
5. Ajuste a configuração e use `/zwotp reload`. Confira o estado com `/zwotp info`.

## Atualizando da 2.x

A atualização é automática e preserva os dados:

- Antes de alterar qualquer arquivo, o plugin copia `config.yml`, `messages.yml` e `warps.yml`
  para `plugins/ZwoTeleport/backups/pre-3.0.0-<data>/`. Isso acontece uma única vez.
- `warps.yml` não é modificado: o formato é o mesmo da 2.x. Mundo, coordenadas, yaw e pitch
  gravados pelo `/setspawn` antigo continuam valendo.
- `config.yml` e `messages.yml` mantêm todo o conteúdo atual. As opções e mensagens novas são
  **acrescentadas ao final** do arquivo; seções novas vêm com os comentários explicativos.
- As chaves antigas alternativas (`teleport.warmup-seconds`, `teleport.cooldown-seconds` etc.)
  continuam aceitas, com um aviso no console.

### Remova o Skript de spawn

O spawn na entrada vem **ativado** com `join-spawn.mode: EVERY_JOIN`, como planejado para o
New Era. Se o servidor tem um Skript (ou EssentialsXSpawn) que envia o jogador ao spawn ao
entrar, **remova esse script** antes de iniciar a 3.0.0; caso contrário o jogador será
teleportado duas vezes. Para manter o Skript temporariamente, use `join-spawn.mode: DISABLED`.

## Spawn na entrada

| Modo | Comportamento |
|---|---|
| `DISABLED` | Nunca envia ao spawn ao entrar. |
| `FIRST_JOIN` | Somente na primeira entrada do jogador neste servidor (sem dados salvos no mundo). |
| `EVERY_JOIN` | Em toda entrada neste servidor. |

- Usa a warp definida em `join-spawn.warp` (padrão `spawn`, a mesma do `/setspawn`), com
  mundo, posição, yaw e pitch exatos.
- Não executa `/spawn`, não aplica warmup, não consome cooldown e não concede permissões.
- O teleporte acontece `join-spawn.delay-ticks` depois da entrada (padrão: 1 tick) e fica preso
  à conexão: se o jogador sair e voltar antes, a tarefa antiga não faz nada.
- Spawn inexistente ou mundo indisponível: o jogador fica onde entrou e o console recebe um
  aviso (no máximo um por minuto).
- `zwo.joinspawn.bypass` ignora o spawn na entrada. **Ninguém tem essa permissão por padrão,
  nem OP**; conceda explicitamente a quem precisar (ex.: construtores).
- Quem saiu marcado em combate volta onde estava na entrada seguinte
  (`join-spawn.skip-if-quit-in-combat`), para que sair e voltar não sirva de fuga.
- Regras de morte, cama, âncora de respawn e respawn não são alteradas.

**Limitação:** o servidor não sabe de onde o jogador veio. Em `EVERY_JOIN`, entrar pelo Hub,
voltar de outra instância e reconectar são tratados da mesma forma. Distinguir uma
transferência específica Hub → Survival exigiria comunicação validada com o Velocity, que não
faz parte desta versão.

## Comandos

| Comando | Permissão | Descrição |
|---|---|---|
| `/tp <jogador>` | `zwo.tp.admin` | Você até o jogador. |
| `/tp <x> <y> <z> [yaw pitch]` | `zwo.tp.admin` | Você até as coordenadas (aceita `~`). |
| `/tp <jogador> <alvo>` | `zwo.tp.admin` | Jogador até o alvo. |
| `/tp <jogador> <x> <y> <z> [yaw pitch]` | `zwo.tp.admin` | Jogador até as coordenadas. |
| `/tpa <jogador>` | `zwo.tpa` | Pede para ir até o jogador. |
| `/tpahere <jogador>` | `zwo.tpahere` | Pede que o jogador venha até você. |
| `/tpaccept [jogador]` (`/tpaceitar`) | `zwo.tpa.respond` | Aceita o pedido (sem nome: o mais recente). |
| `/tpdeny [jogador]` (`/tpnegar`) | `zwo.tpa.respond` | Nega o pedido. |
| `/tpacancel` (`/tpcancelar`) | `zwo.tpa` | Cancela os pedidos enviados. |
| `/warp [nome]` | `zwo.warp.use` | Vai até a warp; sem nome, lista as disponíveis. |
| `/warps` | `zwo.warp.use` | Lista as warps disponíveis. |
| `/setwarp <nome> [nome bonito]` | `zwo.warp.admin` | Cria ou atualiza uma warp na sua posição. |
| `/delwarp <nome>` | `zwo.warp.admin` | Remove uma warp. |
| `/spawn` | `zwo.warp.use` | Vai ao spawn (regras de warp). |
| `/spawn <jogador>` | `zwo.spawn.others` | Envia outro jogador ao spawn, sem warmup. |
| `/setspawn` | `zwo.warp.admin` | Define o spawn, incluindo a direção. |
| `/loja` | `zwo.warp.use` | Vai até a warp `loja`. |
| `/zwotp reload\|info\|combaton\|combatoff` | `zwo.zwotp.admin` | Administração. |

Todos os comandos também existem com namespace, por exemplo `/zwoteleport:tpa`, para
diagnóstico e para quando outro plugin usar o mesmo nome.

### Precedência quando há conflito

1. O ZwoTeleport carrega antes do Essentials, CMI e similares (`loadbefore`), então fica com os
   nomes simples dos comandos do `plugin.yml`. Os comandos do outro plugin continuam
   disponíveis pelo namespace dele (ex.: `/essentials:warp`).
2. `commands.takeover` lista aliases que o ZwoTeleport assume mesmo se outro plugin os
   registrou (padrão: os aliases de TPA). Cada troca aparece no console, e o comando anterior é
   devolvido quando o alias sai da lista ou o plugin é desligado. Nada é feito por reflexão.
3. Atalhos de warp (`/minas`) só funcionam quando **nenhum** plugin registrou um comando com
   esse nome; o ZwoTeleport nunca sobrepõe comandos de outros plugins por atalho.
4. Comandos cancelados por outro plugin (CommandWhitelist, mute, eventos) são respeitados.

## Permissões

| Permissão | Padrão | Efeito |
|---|---|---|
| `zwo.admin` | op | Inclui `zwo.tp.admin`, `zwo.warp.admin`, `zwo.zwotp.admin`, `zwo.spawn.others` e `zwo.tpa.bypass.restrictions`. Ignora cooldown e combate. |
| `zwo.tp.admin` | op | `/tp`. Ignora combate e o cooldown do `/tpahere`. |
| `zwo.warp.admin` | op | Criar/remover warps e spawn; acessa todas as warps; ignora cooldown e combate. |
| `zwo.tpa`, `zwo.tpahere`, `zwo.tpa.respond`, `zwo.warp.use` | todos | Uso comum. |
| `zwo.warp.<nome>` | — | Exigida por warp quando `warps.require-per-warp-permission: true`. |
| `zwo.bypass.cooldown` | op | Ignora cooldown se `teleport.cooldown.allow-bypass-permission: true`. |
| `zwo.tpahere.bypass.cooldown` | op | Ignora cooldown só ao enviar `/tpahere`. |
| `zwo.bypass.warmup` | op | Ignora warmup se `teleport.warmup.allow-bypass-permission: true`. |
| `zwo.bypass.combat` | op | Não é marcado nem bloqueado pelo combate interno. |
| `zwo.tpa.bypass.restrictions` | op | Ignora `tpa.blocked-permissions` e `tpa.blocked-worlds`. |
| `zwo.joinspawn.bypass` | **ninguém** | Não vai ao spawn ao entrar. |
| `zwo.zwotp.admin` | op | `/zwotp`. |

Ser OP não concede nada por fora destas regras: um OP com uma permissão negada no plugin de
permissões perde aquele efeito.

## Warmup, cooldown e combate

- **Warmup:** o jogador espera `teleport.warmup.seconds`. Girar a câmera nunca cancela; andar
  além de `max-horizontal-distance` ou subir/descer além de `max-vertical-distance` cancela.
  Dano, entrar em combate, ser teleportado por outro plugin/portal e sair do servidor também
  cancelam. Ao terminar, tudo é verificado de novo (mundo, coordenadas, combate, outros plugins).
- **Cooldown:** warps, `/spawn` e atalhos verificam o cooldown ao usar o comando e o registram
  **somente quando o teleporte termina com sucesso**; cancelado ou com falha não consome.
  `/tpa` e `/tpahere` verificam e registram ao **enviar** o pedido, para evitar spam.
- **Combate:** dano entre jogadores (inclusive flechas, poções e TNT de outro jogador) marca
  atacante e vítima por `teleport.combat.tag-seconds`. Dano cancelado por proteção de região não
  marca. A tag continua valendo se o jogador sair e voltar antes de expirar.
- `/tp`, `/spawn <jogador>` e o spawn na entrada não têm warmup, cooldown nem bloqueio de combate.

## Rede (Velocity, Hub, Survival, Eventos)

- O ZwoTeleport cuida apenas de teleportes **dentro deste servidor**. `/tpa` nunca leva um
  jogador para outro servidor, e nenhuma função do plugin envia jogadores ao servidor de Eventos.
- Não há mensagens entre servidores nesta versão: nada a autenticar, nada que um cliente possa
  forjar. A autenticação continua com o nLogin no proxy; inventários e UUIDs não são tocados.
- Plugins de transferência (Hub, Eventos) podem consultar a API
  (`isTeleportBlockedByCombat`) para negar a saída de quem está em combate.

## Destino seguro

`safety.<tipo>` define a política por tipo de teleporte: `NONE` (posição exata), `VERIFY`
(recusa se não for seguro) ou `ADJUST` (procura a posição segura mais próxima). Padrões: `/tpa`
usa `ADJUST`; warps, spawn e `/tp` usam `NONE` para não alterar posições definidas pela equipe.
O chunk do destino é carregado de forma assíncrona e a busca roda fora da thread principal,
limitada ao chunk e aos raios configurados.

## Armazenamento

Warps ficam em `warps.yml`. A gravação é atômica (arquivo temporário + troca), a versão anterior
fica em `warps.yml.bak`, e um arquivo corrompido é copiado para `warps.yml.corrompido-<data>`
antes de o plugin recorrer ao backup. Sem backup legível, as gravações ficam bloqueadas para
não sobrescrever o arquivo. Pedidos de teleporte e warmups nunca são gravados.

YAML é o único armazenamento desta versão. A camada `WarpRepository` está pronta para uma
implementação MariaDB/MySQL futura (veja `docs/ARQUITETURA.md`).

## API

```java
ZwoTeleportApi.find().ifPresent(api -> {
    api.teleportToWarp(player, "loja", TeleportOptions.likePlayerCommand())
        .thenAccept(result -> {
            if (result.completed()) {
                // o jogador já está na loja
            }
        });
});
```

Detalhes, eventos e garantias em [`docs/API.md`](docs/API.md).

## Compilar

```bash
./gradlew build
```

O JAR fica em `build/libs/ZwoTeleport-3.0.0.jar`. Os testes rodam no `build`.

## Documentação

- [`CHANGELOG.md`](CHANGELOG.md) — mudanças da 3.0.0
- [`docs/AUDITORIA.md`](docs/AUDITORIA.md) — falhas comprovadas na 2.0.4 e melhorias
- [`docs/API.md`](docs/API.md) — API e eventos
- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) — organização do código e persistência
- [`docs/TESTES.md`](docs/TESTES.md) — testes executados e limitações
