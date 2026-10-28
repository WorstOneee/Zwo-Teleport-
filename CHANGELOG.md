# Changelog

## 3.0.0

Reescrita completa sobre a 2.0.4, mantendo comandos, aliases, permissões, mensagens
e o formato de `warps.yml`. Detalhes das falhas corrigidas em `docs/AUDITORIA.md`.

### Novo

- **Spawn nativo na entrada** (`join-spawn`): modos `DISABLED`, `FIRST_JOIN` e `EVERY_JOIN`
  (padrão), sem warmup, sem cooldown, sem executar `/spawn`, preso à conexão do jogador,
  com bypass explícito `zwo.joinspawn.bypass` e proteção contra fuga de combate.
- **Serviço central de teleporte:** validação de mundo, coordenadas finitas, limites de altura
  e borda; revalidação ao fim do warmup; sucesso anunciado só após a confirmação do servidor.
- **Destino seguro** por tipo de teleporte (`safety`): `NONE`, `VERIFY` ou `ADJUST`, com chunk
  carregado de forma assíncrona e busca fora da thread principal.
- **API e eventos** (`ZwoTeleportApi`, `ZwoPreTeleportEvent`, `ZwoTeleportCompletedEvent`,
  `ZwoTeleportCancelledEvent`, `ZwoTeleportFailedEvent`).
- `/zwotp reload` com validação completa (configuração inválida é recusada e a anterior mantida)
  e `/zwotp info` para diagnóstico.
- Restrições de `/tpa` configuráveis: `tpa.blocked-permissions` e `tpa.blocked-worlds`.
- Tolerância de movimento no warmup configurável; contagem regressiva na actionbar.
- Mensagens em chat, actionbar ou título (`[actionbar]`, `[title]`), hex `&#RRGGBB`, linha com
  os comandos por escrito para quem joga pelo Bedrock.
- Migração automática da 2.x com backup e sem reescrever os arquivos do usuário.

### Corrigido

- Warmup que continuava após sair do servidor e teleportava na reconexão.
- Pedidos de TPA que permaneciam ativos após o jogador sair.
- `/tp` com `NaN`/`Infinity` causando erro interno.
- Mensagem "teleportou X" do `/tp` e do `/spawn <jogador>` enviada antes do teleporte terminar.
- Cooldown gasto em teleportes cancelados ou com falha.
- `/tpa` executado mesmo quando outro plugin (ex.: CommandWhitelist) cancelava o comando.
- `/setwarp` sobre uma warp existente apagava a permissão específica dela.
- Gravação de `warps.yml` não atômica; agora com arquivo temporário, `.bak` e recuperação.
- Warps em mundos carregados depois da inicialização ficavam indisponíveis.
- Nomes de jogadores e warps podiam alterar a formatação das mensagens.
- `/tpa` por trecho do nome podia enviar o pedido ao jogador errado.

### Alterado

- **Assumir aliases de outros plugins** passou a ser configurável (`commands.takeover`), usa só a
  API pública do Paper, registra cada troca no console e devolve o comando ao desligar.
- O bloqueio fixo para o nick "Yarchuww" foi substituído por `tpa.blocked-permissions`
  (padrão: `serpens.yoff.active`, com a mesma mensagem). Agora vale para qualquer jogador com a
  permissão, não para um nome.
- Busca de jogador aceita nome exato ou prefixo único; trechos no meio do nome não são mais aceitos.
- `/zwotp` exige `zwo.zwotp.admin` (incluída em `zwo.admin`). Antes, `zwo.tp.admin` também
  permitia alternar o combate.
- `/zwotp combaton/off` altera apenas `teleport.combat.enabled`.
- `zwo.admin` agora inclui `zwo.tp.admin`, `zwo.warp.admin`, `zwo.zwotp.admin`,
  `zwo.spawn.others` e `zwo.tpa.bypass.restrictions`.
- Bypass baseado só em ser OP (`isOp()`) foi removido; OP continua com as permissões de padrão `op`.
- `/tpa` leva até a posição atual do alvo ao fim do warmup (antes: a posição no momento do aceite).
- Sugestões `zwoteleport:*` continuam visíveis para quem tem `zwo.admin`.
- `api-version: 1.21`; requer Paper 1.21.11 e Java 21.

### Removido

- Interceptação de `/tpa` pelo `PlayerCommandPreprocessEvent` e todo uso de reflexão.
- Remoção automática do prefixo de mensagens que contivesse "zwotp"/"zwoteleport".
