# Testes

Os testes ficam em `src/test/java` e rodam com `./gradlew test` (JUnit 4).

## O que foi verificado e como

### 1. Compilação contra a API real do Paper 1.21.11 (verificação estática)

O código foi compilado com `-Xlint:all` contra o **código-fonte** da `paper-api` da branch
`ver/1.21.11` do PaperMC e do Adventure 4.26.1 (versão usada por essa API), sem avisos. Isso
garante que todas as classes, métodos e assinaturas usadas existem nessa versão.
Bibliotecas que o plugin não usa e que não estavam disponíveis no ambiente (BungeeCord chat,
anotações do Checker Framework, carregador de bibliotecas Maven) foram substituídas por stubs
apenas para essa compilação; nenhum stub entra no JAR.

### 2. Testes unitários (sem servidor)

| Área | Casos principais |
|---|---|
| Pedidos (`RequestManagerTest`) | direção TPA/TPAHERE; autopedido; aceite consome uma vez; só o alvo aceita; substituição; expiração notificada uma vez; pedidos cruzados simultâneos; negar após aceitar; saída remove tudo do jogador. |
| Cooldown, combate, sessão, movimento (`TrackersTest`) | contagem; tag estende sem encurtar; tag sobrevive à reconexão; saída em combate lembrada uma vez; token de sessão muda na reconexão; rotação, pulo e caminhada. |
| Destino (`DestinationValidatorTest`, `SafeLocationFinderTest`) | NaN/Infinity, limites de mundo e altura, `~`, `^`; chão, ar, lava, água, busca do mais próximo, limites do chunk, desistência. |
| Permissões (`BypassRulesTest`, `WarpAccessTest`) | jogador comum, admin, nós específicos com a flag ligada/desligada, bypass do spawn na entrada não dado a admin. |
| Configuração (`SettingsParserTest`, `YamlMigratorTest`, `ConfigFilesTest`) | config padrão válido; config da 2.0.4 válido; aliases antigos; todos os erros reportados de uma vez; migração preserva o texto original, é idempotente, não sobrescreve valores, faz backup uma única vez e não altera `warps.yml`. |
| Persistência (`YamlWarpRepositoryTest`) | leitura do formato 2.x (yaw/pitch); entradas inválidas e chaves desconhecidas preservadas; `.bak`; arquivo corrompido recuperado do backup sem ser apagado; sem backup fica somente leitura. |
| Mensagens (`MessageTest`) | canais chat/actionbar/título; mensagem vazia desativa; reserva para chaves ausentes; mensagens `<warp>.teleported` da 2.x. |

### 3. Testes de comportamento em servidor simulado

`src/test/java/.../fake` implementa, com proxies dinâmicos sobre as **interfaces reais** da
paper-api, um servidor mínimo: jogadores, mundos, agendador por ticks e disparo de eventos. Os
serviços reais do plugin rodam sobre ele.

| Área | Casos |
|---|---|
| `TeleportServiceBehaviorTest` | warmup completo; sucesso só após confirmação do servidor; recusa do servidor sem sucesso nem cooldown; cooldown após sucesso; bypass; flag de bypass desligada; caminhar cancela, girar/pular não; dano; combate cancela e bloqueia; bypass de combate; `/tp` ignora combate; **desconexão durante warmup e reconexão sem efeito**; substituição; mundo descarregado durante o warmup; coordenadas inválidas; bloqueio por outro plugin em START e EXECUTE; alvo do TPA sai; TPA segue o alvo; **desligamento com teleporte pendente**; contagem na actionbar; jogador morto; falha ao carregar chunk. |
| `TpaBehaviorTest` | TPA e TPAHERE movem a pessoa certa; aceite duplo teleporta uma vez; negar após aceitar; quem pediu não aceita o próprio pedido; autopedido; cooldown no envio; bypass só do TPAHERE; permissão bloqueada com a mensagem original; mundos bloqueados; saída notifica só a outra parte; pedido substituído; cancelamento; warmup e movimento após aceite. |
| `JoinSpawnBehaviorTest` | **toda entrada** leva à posição/rotação exatas sem warmup, mensagem ou cooldown; **primeira entrada**; desativado; bypass explícito e admin sem bypass; **reconexão rápida teleporta uma vez**; **spawn ausente**; **mundo indisponível**; saída em combate não vira fuga; combate não cancela o spawn na entrada. |
| `WarpServiceBehaviorTest` | warp visível na hora e gravada depois; arquivo ilegível bloqueia alterações; reload de arquivo quebrado mantém as warps em memória. |
| `ApiBehaviorTest` | warps e disponibilidade; teleporte pela API; cancelamento; pedidos e combate; warp inexistente. |

Total nesta entrega: 157 testes, todos passando.

## O que **não** foi testado

- **Execução em um servidor Paper real.** O ambiente de desenvolvimento não tinha acesso aos
  repositórios do PaperMC para baixar o servidor. O servidor simulado não carrega chunks, não
  tem física, rede, Brigadier nem outros plugins. Em particular, ficaram sem teste em execução:
  - a busca de posição segura com chunks reais (a lógica está coberta por testes unitários);
  - a troca de dono dos aliases (`commands.takeover`) no mapa de comandos do Paper;
  - atalhos de warp como comando e a ocultação de sugestões com namespace;
  - convivência com Essentials, CommandWhitelist, nLogin e o plugin de eventos.
- `./gradlew build` não foi executado neste ambiente pelo mesmo motivo (sem acesso ao repositório
  do PaperMC). O JAR entregue foi gerado com `javac` contra a API do Paper e testado como
  descrito acima.

## Roteiro para o servidor de testes

Antes de colocar no New Era, valide em uma cópia do servidor:

1. Inicie com os arquivos da 2.x e confira o backup em `plugins/ZwoTeleport/backups/` e as
   linhas novas no fim de `config.yml`/`messages.yml`.
2. `/zwotp info`: spawn definido, modo do spawn na entrada, comandos assumidos.
3. Entre pelo Hub: deve aparecer no spawn, olhando para a direção do `/setspawn`, uma única vez.
4. Saia e volte rapidamente (menos de 1 s): um único teleporte.
5. Marque-se em combate com outra conta, vá ao Hub e volte: deve continuar onde estava.
6. `/tpa` e `/tpahere` entre duas contas, aceite pelo botão e por `/tpaccept <nome>`; aceite duas vezes.
7. Durante o warmup: gire a câmera (continua), pule (continua), ande (cancela), tome dano (cancela).
8. `/warp`, `/loja`, um atalho de warp e `/zwoteleport:warp` com o Essentials instalado.
9. `/tp` com `~`, com `NaN` (deve recusar) e para outro jogador (mensagem só após o teleporte).
10. Com o CommandWhitelist bloqueando `/tpa` para um grupo: o comando deve continuar bloqueado.
11. `/zwotp reload` com um erro proposital no `config.yml`: deve recusar e manter o anterior.
12. Pare o servidor durante um warmup: sem erros no console.
