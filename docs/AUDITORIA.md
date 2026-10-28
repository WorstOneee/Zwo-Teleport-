# Auditoria do ZwoTeleport 2.0.4-Yago-LojaFix

Base analisada: o JAR distribuído (classes compiladas, `plugin.yml`, `config.yml`, `messages.yml`),
lido instrução por instrução com `javap`. Cada item abaixo indica a classe/método onde o
comportamento foi observado.

## Falhas comprovadas

| # | Falha | Onde | Efeito |
|---|---|---|---|
| 1 | Warmup sobrevive à desconexão e age na reconexão. | `TeleportService`: não há tratamento de `PlayerQuitEvent`; a tarefa do warmup (`lambda$teleport$1`) busca o jogador por UUID ao terminar. | Se o jogador sair e voltar antes do fim do warmup, o teleporte antigo é executado na nova conexão. Pedidos de TPA também ficam vivos até a limpeza periódica (a cada 15 s). |
| 2 | `/tp` aceita coordenadas não finitas. | `TpAdminCommand.parseNumber` usa `Double.parseDouble` sem validar. | `/tp NaN 64 0` ou `Infinity` chegam ao `teleportAsync`, que lança exceção (`Location.checkFinite`): erro interno no comando. |
| 3 | Sucesso anunciado antes da conclusão. | `TpAdminCommand` (formas com outro jogador) e `SpawnCommand` (`/spawn <jogador>`). | O remetente recebe "teleportou X" logo após pedir o teleporte, mesmo que ele falhe ou seja recusado por outro plugin. |
| 4 | Cooldown consumido antes do teleporte. | `WarpTeleportHandler.teleport` chama `consumeCooldown` antes do warmup. | Cancelar por movimento/dano ou falhar ainda gasta o cooldown. |
| 5 | Interceptação de `/tpa` ignora cancelamentos e namespaces. | `TpaCommandPreprocessListener`: `PlayerCommandPreprocessEvent`, prioridade `LOWEST`, `ignoreCancelled=false`; `normalizeRoot` descarta qualquer prefixo `plugin:`. | Comandos já cancelados por outros plugins (ex.: CommandWhitelist, mute) são executados; `/essentials:tpa` também é capturado. |
| 6 | Regra fixa por nome de jogador. | `TpaCommandPreprocessListener.isYarchuwwBlocked` compara o nick com "Yarchuww". | Nome não é identidade confiável em rede com jogadores não originais; regra e mensagem só mudam recompilando. |
| 7 | Reflexão em internals do servidor e do próprio plugin. | `CommandOwnershipService` (`knownCommands`, `getCommandMap`, `syncCommands` por reflexão), `ShortcutWarpListener` (`getCommandMap` por reflexão), `ZwoTpCommand` (campo privado `combatUntil`). | Frágil a atualizações do Paper; a troca de dono dos comandos não era registrada. |
| 8 | Gravação de warps não atômica e sem backup. | `WarpService.save` usa `FileConfiguration.save(file)` diretamente. | Uma falha durante a escrita pode deixar `warps.yml` truncado. |
| 9 | Atualizar uma warp apaga a permissão específica. | `WarpService.setWarp` cria a warp com permissão vazia. | `/setwarp vip` sobre uma warp com `permission: serpens.vip` a torna pública. |
| 10 | Busca de jogador por trecho do nome. | `CommandUtil.resolveOnlinePlayer`: sem prefixo único, aceita `contains`. | `/tpa los` envia o pedido para "Carlos". |
| 11 | Placeholders interpretados como formatação. | `MessageService.apply` troca `{player}` etc. no texto e só depois converte os códigos `&`. | Um valor com `&` altera cores; em `/warp <nome>` o texto digitado entra na mensagem formatado. |
| 12 | Warps de mundos carregados depois são descartadas. | `WarpService.reload` ignora warps cujo mundo não existe no momento (recarga única 2 s após iniciar). | Warps em mundos carregados mais tarde ficam indisponíveis até um reload manual. |
| 13 | Sugestões com namespace escondidas de todos. | `CommandVisibilityListener` remove `zwoteleport:*` de qualquer jogador, inclusive administradores. | Dificulta diagnóstico; os comandos continuavam funcionando. |

## Comportamentos revistos (não eram falhas, mas foram melhorados)

- **Destino do `/tpa`:** era a posição do alvo no momento do `/tpaccept`. Agora é resolvida ao
  fim do warmup (o jogador vai até onde o alvo está), e o teleporte é cancelado se o alvo sair.
- **Movimento no warmup:** a 2.x comparava blocos. Girar não cancelava (correto), mas pular
  cancelava ao trocar de bloco no eixo Y, e andar meio bloco podia ou não cancelar dependendo da
  posição. Agora a tolerância é configurável e independente da grade.
- **Bypass por OP:** `isOp()` concedia bypass mesmo com a permissão negada. Agora tudo passa por
  permissões (OP continua recebendo as de padrão `op`).
- **`/zwotp combaton/off`:** reescrevia cinco opções do `config.yml`. Agora altera apenas
  `teleport.combat.enabled`, preservando o restante.
- **Prefixo de mensagens:** a 2.x apagava o prefixo se ele contivesse "zwotp"/"zwoteleport".
  Agora o prefixo é usado como configurado.

## Verificado sem falha

- Aceitar um pedido o removia de forma síncrona: não havia aceite duplo.
- A tag de combate já ignorava dano cancelado (`ignoreCancelled=true`) e identificava o
  atirador de projéteis.
- Rotação da câmera não cancelava o warmup.
- O teleporte usava `teleportAsync` e a mensagem de sucesso do próprio jogador vinha após a
  conclusão (o problema do item 3 é só na mensagem para quem executou o comando).
- A 2.0.4 não fazia spawn na entrada; não havia conflito interno com o Skript.
