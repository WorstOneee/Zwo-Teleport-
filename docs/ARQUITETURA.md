# Arquitetura

## Pacotes

| Pacote | Responsabilidade |
|---|---|
| `api`, `api.event` | Contrato público para outros plugins: `ZwoTeleportApi`, opções, resultados e eventos. |
| `teleport` | `TeleportService` (execução central, warmup, validação, eventos), trackers de cooldown, combate e sessão, regras de bypass, validação de coordenadas. |
| `teleport.safety` | Busca de posição segura (`SafeLocationFinder`) sobre um `BlockGrid`; `SnapshotBlockGrid` adapta um `ChunkSnapshot`. |
| `request` | Pedidos de `/tpa` e `/tpahere`: `RequestManager` (estado puro) e `TpaService` (regras e mensagens). |
| `warp` | Modelo de warp, nomes, acesso, `WarpService` (memória + gravação assíncrona) e `WarpTeleporter`. |
| `spawn` | Spawn na entrada: modo, decisão pura e `JoinSpawnService`. |
| `storage` | `WarpRepository`, implementação YAML e gravação atômica. |
| `config` | Leitura e validação do `config.yml`, migração de arquivos e utilitários YAML. |
| `message` | Catálogo de mensagens e envio por chat/actionbar/título. |
| `command`, `listener` | Adaptadores finos entre o Paper e os serviços. |

As regras de negócio que não precisam do servidor (pedidos, cooldown, combate, sessão,
tolerância de movimento, segurança do destino, permissões, validação e migração de
configuração, persistência YAML) ficam em classes sem dependência do Bukkit e têm testes
unitários próprios.

## Modelo de execução

- Tudo que toca jogadores, mundos e eventos roda na thread principal.
- O chunk de destino é carregado com `World#getChunkAtAsync`; a busca de posição segura roda em
  uma tarefa assíncrona sobre um `ChunkSnapshot` imutável; o resultado volta para a thread
  principal antes de chamar `Player#teleportAsync`.
- Gravações de warps rodam em uma thread dedicada, uma de cada vez, na ordem em que foram feitas.
- Cada teleporte guarda o UUID do jogador e o número da sessão (conexão). Tarefas atrasadas
  conferem ambos e o estado do teleporte antes de agir; nenhuma referência a `Player` é guardada.
- O reload valida `config.yml` e `messages.yml` por completo antes de trocar qualquer coisa. Se
  houver erro, nada muda. Teleportes em andamento continuam; os novos usam a configuração nova.
- No desligamento: comandos assumidos são devolvidos, teleportes pendentes são cancelados
  (`SHUTDOWN`), pedidos são descartados e as gravações pendentes de warps são concluídas
  (até 10 s).

## Persistência

YAML (`warps.yml`) é o padrão e o único implementado nesta versão. O formato é o mesmo da 2.x.

`WarpRepository` isola o armazenamento. Uma futura implementação MariaDB/MySQL deve seguir:

- **Opcional:** selecionada por `storage.type`; sem banco configurado, YAML continua.
- **Escopo:** warps pertencem a uma instância (coluna `server_id`), porque cada servidor tem seus
  próprios mundos. Compartilhar só faz sentido para warps marcadas explicitamente como globais.
  O spawn é sempre por instância.
- **Execução:** consultas parametrizadas, pool pequeno e limitado, nunca na thread principal
  (o `WarpService` já chama o repositório fora dela).
- **Esquema:** migrações versionadas (tabela de versão do esquema), aplicadas na inicialização.
- **Migração YAML → SQL:** comando explícito, com backup do `warps.yml` e conferência da
  contagem/valores após importar.
- **Falha:** se o banco estiver indisponível, o plugin informa e bloqueia alterações; não alterna
  sozinho para YAML, o que dividiria os dados em duas fontes.
- Pedidos de teleporte, warmups, cooldowns e tags de combate não são persistidos.

## Integração com a rede

O plugin não envia nem recebe mensagens entre servidores. Se uma integração com o Velocity for
necessária no futuro (por exemplo, distinguir Hub → Survival), ela deve:

- validar a origem das mensagens no proxy, não confiar em nome de servidor ou de jogador vindo do cliente;
- usar autorizações com expiração e uso único;
- não usar o segredo de forwarding como autorização para mensagens arbitrárias;
- separar "pode solicitar transferência" de "pode entrar no evento".
