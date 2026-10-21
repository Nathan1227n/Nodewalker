# Caminhos mínimos — revisão acadêmica de 19/10/2026

## Dois problemas

Após a PR #34, a exigência acadêmica foi revista para incluir dois problemas distintos:

- **TSP:** `BruteForceSolver` e `BacktrackingSolver` encontram o ciclo hamiltoniano mínimo, visitando todas as cidades e retornando à origem 0. Seus contratos e testes permanecem preservados.
- **Caminhos mínimos:** `DijkstraShortestPathSolver` calcula distâncias de uma origem aos vértices alcançáveis no grafo de conexões disponíveis. Um caminho não precisa visitar todas as cidades nem retornar à origem.

Dijkstra clássico **não resolve o TSP**. Esta implementação não usa máscaras de subconjuntos, `TspSolver` ou `TspResult`. `SearchSpace.completeRoutes(N)` continua sendo exclusivamente o espaço fatorial do TSP. A proposta anterior de Dijkstra sobre estados do TSP foi abandonada antes de qualquer implementação.

A revisão acrescenta a nova exigência aos DOC 01/02/03, preservando seu histórico. A exclusão anterior de Dijkstra é substituída quanto ao escopo geral: ele passa a resolver um problema separado, sem mudar o contrato matemático do TSP.

## Grafo e topologias

`RefugeGraph` associa o `TspProblem` original à `RefugeTopology` imutável. Os vértices são os mesmos índices, N>=3. O grafo é simples, não direcionado, sem laços ou conexões paralelas. Cada conexão existente usa **exatamente `problem.cost(u,v)`**. Zero e `Long.MAX_VALUE` são pesos legítimos; ausência de conexão pertence à topologia, nunca a um custo numérico. O modelo garante pesos não negativos e simetria.

Coordenadas e terreno não substituem custos. Matrizes manuais podem ser não métricas. O arredondamento das distâncias geradas também impede presumir que toda aresta direta seja um caminho mínimo.

| Topologia | Representação |
| --- | --- |
| `COMPLETE` | Todos os pares distintos, vizinhos implícitos; não duplica a matriz em adjacências. |
| `SPARSE_CONNECTED` | Cadeia embaralhada e conexões extras, com conectividade garantida. |
| `MANUAL` | Lista copiada/validada; admite componentes desconectados e origem isolada. |

`degree(v)` e `neighborAt(v,p)` expõem vizinhos crescentes sem entregar arrays mutáveis. A ordem de entrada das arestas não afeta resultados. Orientações são normalizadas; duplicatas, laços, índices inválidos e incompatibilidade com o problema são recusados.

### Geração esparsa reproduzível

`RefugeTopologyGenerator.VERSION = "chain-chords-v1"`. Um `Random(topologySeed)` embaralha os índices por Fisher–Yates. Pares consecutivos formam uma cadeia que inclui todos os vértices, provando conectividade. As N−2 posições candidatas a conexões `order[i]`—`order[i+2]` também são embaralhadas; as primeiras `extraConnections` são acrescentadas.

Há exatamente `N−1+extras` conexões, até `2N−3`. Não há rejeição aleatória sem limite ou varredura de todos os pares. Construção esperada e armazenamento são lineares: deduplicação por hash e adjacências de grau limitado. Sem extras, há caminhos intermediários simples; extras oferecem alternativas. Em N pequeno, o subconjunto pode coincidir com o completo.

Registrar N, seed da instância, seed da topologia, extras e versão. A topologia conserva seed/versão; extras são `edgeCount()−(N−1)`. A geração controlada recebe seu próprio `GenerationCancellationToken` e orçamento. Sua `CancellationException` não é resultado de solver. Interrupção da thread é preservada.

## API

```java
import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;

var problem = TspGenerator.generate(8, 657L, new GenerationOptions(100_000, 64L * 1024 * 1024));
var topology = RefugeTopologyGenerator.connectedSparse(8, 23L, 3);
var graph = new RefugeGraph(problem, topology);
var options = ShortestPathExecutionOptions.demonstration();
ShortestPathPreflight.analyze(graph, options).requireAllowed();
var token = new ShortestPathCancellationToken();
var result = new DijkstraShortestPathSolver().solve(graph, 0, options, token);
var distance = result.distanceTo(7); // Optional<BigInteger>, somente confirmada
var path = result.pathTo(7);         // Optional<List<Integer>>, imutável
var state = result.vertexState(7);
```

`solve(graph, options, token)` usa origem 0. `solve(graph)` usa origem 0, token novo e limites de demonstração, não uma execução ilimitada. O solver aceita qualquer origem válida. Outra thread pode chamar `token.cancel()`; o token é persistente/idempotente e sem reset.

O resultado conserva grafo, origem, status, motivo e métricas. Trocar somente o destino reutiliza distâncias/predecessores. `pathTo(origin)` retorna `[origin]`, com distância zero quando fixada; seu predecessor fica ausente. A API é síncrona e independente de JavaFX: a futura UI deve executá-la em worker.

## Algoritmo e correção

O algoritmo mantém rótulos, predecessores e fila mínima indexada. Inicialmente apenas a origem possui distância zero. Cada extração mínima fixa definitivamente um vértice; suas conexões são examinadas e podem melhorar estritamente rótulos de vizinhos ainda não fixados. Cada conexão usa a matriz original. A execução termina corretamente quando a fila esvazia e o checkpoint final permite concluir.

Com pesos não negativos, o menor rótulo retirado não pode ser melhorado passando primeiro por um vértice ainda não fixado. Assim, distâncias fixadas são mínimas. Relaxamentos descobrem os vértices ao longo de qualquer caminho alcançável; esgotar a fila prova a ausência de caminho para os restantes. Referência: [Dijkstra e fila mínima indexada, Princeton](https://algs4.cs.princeton.edu/code/javadoc/edu/princeton/cs/algs4/DijkstraSP.html).

### Empates e predecessores

1. Fila compara `(distância, índice)`.
2. Vizinhos são examinados em ordem crescente.
3. Igualdade conserva o predecessor estabelecido.

Isso é determinístico mesmo sob permutações das arestas manuais, sem prometer caminho lexicograficamente mínimo ou menos arestas. O teste literal `[0,2,3]` versus `[0,1,2,3]`, ambos de custo 3, documenta essa diferença.

Predecessores são atribuídos de um vértice fixado para outro ainda não fixado. Vértices fixados nunca são atualizados; a ordem de fixação diminui estritamente ao seguir predecessores. Portanto não há ciclos, inclusive com pesos zero. A fábrica interna do resultado verifica essa ordem, disponibilidade e equação de distância de cada predecessor. Reconstruir um caminho custa O(comprimento) e entrega uma lista imutável.

## Distâncias e resultados parciais

Rótulos, somas e distâncias públicas usam `BigInteger`, sem sentinela de infinito. Pesos `Long.MAX_VALUE` e 1 em uma cadeia produzem distância válida **9223372036854775808**. Candidatos acima de `long` que não melhoram um rótulo também são comparados exatamente. Essa política é própria dos caminhos mínimos; o overflow em `long` dos solvers TSP permanece preservado.

| Situação | Distância/caminho público |
| --- | --- |
| `CONFIRMADO` | Após extração mínima/fixação, mesmo se a varredura das adjacências for interrompida. |
| `INALCANCAVEL` | Ausente, somente depois de `CONCLUIDO` provar ausência de caminho. |
| `NAO_RESOLVIDO` | Ausente em execução interrompida sem fixação. Rótulos tentativos são omitidos. |

`allDistancesResolved()` só é verdadeiro em `CONCLUIDO`. Uma execução parcial conserva apenas distâncias individuais já provadas. Interrupção no checkpoint final continua parcial, mesmo com todos os vértices fixados; os restantes não são classificados como inalcançáveis.

## Controles

Opções exigem limites não negativos de vértices, entradas examinadas e milissegundos, além de orçamento positivo de bytes estimados. Milissegundos devem ser conversíveis exatamente em nanossegundos `long`. Perfil inicial: **10.000 vértices, 1.000.000 entradas, 2.000 ms, 64 MiB auxiliares estimados**. É perfil de demonstração, sem promessa universal de desempenho.

- K admite K operações; só tentar a seguinte esgota o orçamento. Conclusão natural exatamente em K é permitida.
- Vértices contam na fixação, incluindo origem. Orçamento zero de vértices impede alocar workspace.
- Entradas contam antes do exame, inclusive se o vizinho já está fixado. Orçamento zero de entradas permite concluir origem isolada.
- Tempo expira em `elapsed >= millis*1_000_000`. A subtração modular aceita passagem de `nanoTime` pelo limite numérico; duração negativa/não representável é erro.
- Checkpoints coletam token, sinal da thread, tempo e orçamento pertinente. Falhas reais nessa coleta resultam em `ERRO`. Entre condições coletadas: cancelamento → tempo → vértices → entradas.
- Há checkpoints na preparação, alocação, fixação, adjacências e finalização. Alocações, somas e cópia/validação de resultado são indivisíveis: não há garantia de latência máxima ou encerramento forçado.
- `RuntimeException` durante execução resulta em `ERRO`, com confirmações válidas quando disponíveis. Falha real na validação do resultado é sempre `ERRO`, inclusive após cancelamento ou limite; o diagnóstico conserva o estado anterior e a exceção, e todo o snapshot inválido é omitido. Interrupções com snapshot válido conservam seu estado e suas distâncias confirmadas. Falhas secundárias de medição são diagnosticadas. `Error` da JVM, inclusive OOME, é propagado.

Recusa de preflight/argumento inválido ocorre antes da execução e do relógio, sem fabricar resultado interrompido. O pedido posterior ao último checkpoint pode coincidir com conclusão; um resultado já publicado é imutável.

### Memórias separadas

| Componente | Fronteira |
| --- | --- |
| Solver | Modelo `256*N + 1 MiB`, incluindo reserva para rótulos, predecessores, fixação, heap e snapshot. O orçamento de 64 MiB aplica-se somente aqui. |
| Topologia explícita | Modelo conservador de construção/armazenamento `256*N + 128*E + 4096`, com orçamento separado, padrão 64 MiB. Completa implícita informa reserva de 1024 bytes. |
| `TspProblem` | Payload existente `8*N²`, sem overhead. A geração usa o próprio modelo de pico `16*N² + 256*N + 1024` e limites de candidatos/memória. |

Estimativas não são medições de layout da JVM, reservas de heap ou garantia contra OOME. Usam `BigInteger`; índices/dimensões são conferidos antes das alocações. Topologia completa permite analisar metadados enormes sem adjacências. Uma lista manual já construída pelo chamador está fora do controle da fábrica.

N livre na interface futura exigirá orçamentos de geração, topologia, solver e visualização. Memória auxiliar linear não elimina a matriz O(N²).

## Métricas e complexidade

`ShortestPathMetrics` não reutiliza folhas/permutação/poda do TSP.

| Métrica | Definição |
| --- | --- |
| `processedVertices` | Extrações com fixação definitiva, inclusive origem; não significa varredura completa das adjacências. |
| `examinedEdges` | Entradas de adjacência examinadas; conexão não direcionada pode contar duas vezes. |
| `relaxationAttempts` | Candidatos para vizinhos ainda não fixados. |
| `successfulRelaxations` | Melhoras estritas, inclusive primeira descoberta. |
| `peakQueueSize` | Ocupação máxima; uma entrada por vértice, sem duplicatas obsoletas. |
| `elapsedNanos` | Preparação/alocação/busca até consolidação temporal. Exclui preflight, geração/topologia, cópia/validação final, reconstrução sob demanda e renderização. Ausente se medição falhar. |

| Algoritmo | Tempo de pior caso | Espaço auxiliar de busca |
| --- | --- | --- |
| Força bruta TSP | O(N·(N−1)!) | O(N) |
| Backtracking TSP | O(N·(N−1)!) | O(N), incluindo pilha |
| Dijkstra com heap indexado | O((V+E) log V) operações aritméticas; completo O(V² log V), esparso gerado O(V log V) | O(V), além da topologia e matriz |

Somas/comparações de `BigInteger` dependem dos bits das distâncias. Rótulos são somas de no máximo V−1 pesos `long`, pois a floresta é simples.

## Auditoria e experimento

`FloydWarshallOracle` é exclusivamente de testes, com guarda N<=12 antes da tabela. Calcula distâncias por DP em `BigInteger`, sem usar Dijkstra, heap, predecessores ou seu percurso de adjacências. Consulta disponibilidade por pares e lê diretamente a matriz. O próprio oráculo é conferido com tabela literal de distâncias/desconexões.

A auditoria cobre **300 combinações** N=3..7, seeds 0..19, topologias completa/esparsa/manual e **todas as origens**, com zeros, máximos e desconexões. Há casos manuais, intermediários, empates, seeds extremas euclidianas, validação independente de caminhos/somas, imutabilidade e preservação da matriz. Controles incluem K−1/K/K+1 de vértices e entradas, relógio injetado, interrupção em todas as fases, falhas reais e cancelamento por barreira entre threads. O heap é confrontado com mínimos por varredura independente. Não se executa força bruta para N grande.

```bash
mvn -pl tsp-core test
mvn clean verify
git diff --check
java --class-path tsp-core/target/classes tools/ShortestPathBenchmark.java
```

O experimento opcional usa dois aquecimentos/cinco medições, N=8/32/128, seeds 0/657, mesma instância para ambas as topologias. Confere determinismo e só publica mediana se todas as execuções concluírem. Não executa TSP, não mede heap real/FPS e não estabelece ranking entre problemas diferentes. Dados observados: [benchmarks/dijkstra-classic-linux.txt](benchmarks/dijkstra-classic-linux.txt).

### Evidência local da PR A

Em 19/10/2026, Fedora 44/Linux amd64, Temurin 21.0.12.1 e Maven 3.9.11: `env -u DISPLAY -u WAYLAND_DISPLAY mvn --batch-mode --no-transfer-progress clean verify` passou com **2214 testes** (2022 core, 125 CLI, 67 app), zero falhas/erros/ignorados, cerca de 9,2 s no Maven. São **370 casos novos**, preservando os 1844 anteriores. O workflow continua executando o mesmo produto e a spike separada em Ubuntu/Windows. Os oráculos e asserções de teste não entram no JAR de produção.

Revisão da PR #35: dez testes adicionais exercitam falhas reais do validador (distância inconsistente e predecessor cíclico) em todos os estados terminais, exigindo sempre `ERRO` sem publicar o snapshot inválido; interrupções válidas preservam estado e confirmações. A suíte passa a **2224 testes** (2032 core, 125 CLI, 67 app). A comparação dos DOC 01/02/03 com a main, incluindo os dez parágrafos adicionados e preservação byte a byte do XML histórico, está em [REVISAO_DOCX_PR35.md](REVISAO_DOCX_PR35.md).

O experimento executou 84 buscas (12 combinações, sete chamadas cada), todas concluídas. Em N=128, as medianas internas observadas foram aproximadamente 0,779–1,142 ms no completo e 0,028–0,044 ms no esparso. São poucas amostras locais, com aquecimento curto e topologias diferentes; não demonstram desempenho universal. Não houve medição de heap real.

Exemplo reproduzível observado: N=32, seed da instância/topologia=657, extras=16. Completo: distância 0→31 de 371 pela aresta direta. Esparso: distância 7802 pelo caminho `[0,8,20,21,29,17,27,26,16,14,9,25,13,23,31]`. Ambos usam a mesma matriz e cada caminho respeita suas conexões disponíveis. A diferença é esperada pela restrição de topologia.

## Integração posterior

PR B (#12): selecionar TSP/força bruta, TSP/backtracking ou caminhos mínimos/Dijkstra; origem/destino/topologia explícitos; geração e busca em worker, fases/tokens separados, proteção contra resultados obsoletos e HUD consolidado. Adaptador próprio mostrará conexões disponíveis e somente o caminho selecionado em destaque no 3D/2D. O texto será “caminho mínimo”, sem certificação TSP. Comparações devem respeitar problema/grafo/origem.

PR C (#13): clone limpo, demonstração dos dois problemas, fluxos completos, CI Ubuntu/Windows e evidências gráficas. O painel e a demonstração visual de Dijkstra continuam pendentes nesta PR A.


## Integração no painel — 19/10/2026

A entrega da issue #12 utiliza estes contratos sem alterá-los. Origem/destino, topologias completa/esparsa,
execução assíncrona, cancelamento e destaque do caminho estão descritos em [PAINEL_OPERACIONAL.md](PAINEL_OPERACIONAL.md).
O painel não oferece editor manual e não trata caminhos mínimos como solução TSP.

## Revalidação do MVP — 21/10/2026

PRs #35/#36/#37 mescladas; a [coleta da #13](VALIDACAO_MVP.md) conserva os 380 testes de caminhos mínimos/topologia, a auditoria Floyd–Warshall e os casos de interrupção/validação. Nenhum defeito exigiu alterar o núcleo. As passagens acima que descrevem PR B/PR C futuras são o registro da PR A, não o estado atual.

O [roteiro acadêmico](DEMONSTRACAO_ACADEMICA.md) usa N8/seed657, esparso seed23/extras0: origem0/destino5, caminho 0–2–3–7–5, distância 3463. Destino2 reutiliza a resposta e mostra 877. A cadeia manual confirma 2×MAX=18446744073709551614, sem sentinela de infinito. [Saída dos solvers reais](evidencias/issue-13/academic-reference.txt) e [benchmark controlado](benchmarks/issue-13/README.md). Grafos dirigidos, inclusive o exercício dos slides, continuam fora do modelo atual. Homologação gráfica Windows e interação humana permanecem pendentes.
