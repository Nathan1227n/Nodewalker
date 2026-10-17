# Controle de execução dos solvers — issue #9

Contrato do `tsp-core`, sem JavaFX. Fontes: DOC 02 §§8–9, DOC 03 §§12, 14 e TSP-09/10/13, issue #9 e revisão técnica. Os controles não alteram custos, geração, ordem lexicográfica, desempate ou poda.

## API e compatibilidade

```java
TspSolver solver = new BacktrackingSolver();
SolverCancellationToken token = new SolverCancellationToken();
SolverExecutionOptions options = SolverExecutionOptions.limits(1_000_000, 2000);
SolverPreflight report = SolverPreflight.analyze(SolverAlgorithm.BACKTRACKING, problem.size(), options);
report.requireAllowed();
TspResult result = solver.solve(problem, options, token);
// Outra thread pode chamar token.cancel().
```

`TspSolver` continua sendo uma interface funcional, com um único método abstrato `solve(TspProblem)`. Lambdas e implementações anteriores continuam válidas. A nova sobrecarga padrão lança `UnsupportedOperationException`; ambos os solvers do núcleo a implementam. Uma implementação antiga não pode aceitar controles e ignorá-los silenciosamente.

**A chamada legada `solve(problem)` não recebe automaticamente limites, política operacional, cancelamento ou observação de interrupção da thread.** Seu resultado, ordem, contadores e duas leituras de relógio são preservados. Ela continua sendo uma operação de baixo nível; a API controlada é a entrada recomendada para integrações novas.

As opções são imutáveis. `nodeLimit(K)`, `timeLimitMillis(T)` e `limits(K,T)` ativam orçamentos explícitos; `OptionalLong.empty()` desativa cada limite individualmente no construtor público. `unlimited()` autoriza explicitamente desativar ambos, sujeito à política por algoritmo/N. Ausência de limites sem autorização é inválida. `withPolicy(policy)` cria outra configuração sem modificar a original.

Nulos e opções inválidas falham antes da busca. Recusa de preflight lança `IllegalArgumentException`, também antes de iniciar o cronômetro; não é um solver interrompido e não fabrica `TspResult`. Falhas observadas durante a execução são tratadas como descrito abaixo.

## Orçamento de nós e significado dos contadores

Um orçamento **K admite exatamente K estados**. Apenas a tentativa de processar outro estado atinge o limite. A conclusão natural após o K-ésimo estado retorna `CONCLUIDO`, desde que nenhuma outra condição interrompa a execução.

- Força bruta: um estado é uma permutação cujo processamento começou. A verificação precede o incremento e o cálculo do custo.
- Backtracking: um estado é uma entrada processada na recursão, incluindo raiz, estados internos, folhas e podas. O orçamento também é verificado antes de calcular a aresta para um filho. Um filho impedido não calcula custo, não conta nó e não registra poda.
- Verificações dentro de um estado já admitido observam cancelamento e tempo. Elas não consomem nós adicionais nem impedem processar o próprio K-ésimo estado.
- K=0 impede iniciar exploração; negativos são inválidos. `Long.MAX_VALUE` é um orçamento de nós válido. Contadores continuam usando incrementos exatos.

O triângulo manual de custo 45 precisa de dois estados na força bruta e cinco no backtracking. Respectivamente, K=1/K=4 interrompem com incumbente 45; K=2/K=5 concluem. São testes específicos, além de K+1.

## Tempo e checkpoints

O limite é informado em milissegundos inteiros não negativos. A conversão para nanossegundos é exata; valores acima de `Long.MAX_VALUE / 1_000_000` são rejeitados. Zero expira antes da exploração. O prazo é atingido quando `elapsedNanos >= maxElapsedMillis * 1_000_000`.

`System.nanoTime()` mede desde antes da preparação da busca até depois da canonicalização e consolidação/conferência, antes da fábrica validada do resultado. Preflight, geração, renderização, impressão e validação final da fábrica ficam fora desse intervalo. A subtração modular aceita origem negativa e passagem pelo limite numérico de `long` enquanto a duração é representável e não negativa; duração inválida resulta em erro.

Há checkpoints antes da preparação, antes de cada estado, entre arestas e iterações da força bruta, nas iterações/descidas do backtracking e na finalização. O checkpoint final usa a mesma leitura final do cronômetro, sem consultar novamente o relógio parado. Sem limite temporal, não há leituras intermediárias; o caminho legado mantém exatamente duas.

O prazo é **cooperativo**, não um timeout que mata threads. Alocações, cópias de incumbente, validação e consolidação já iniciadas podem terminar antes da próxima observação. A produção de métricas/resultado pode fazer a duração observada ultrapassar o orçamento. Não há garantia de latência máxima de cancelamento. A pilha do backtracking continua O(N); não há garantia de memória para qualquer N aceito.

## Cancelamento, estados e precedência

`SolverCancellationToken` usa `AtomicBoolean`: o pedido é thread-safe, persistente e idempotente, sem reset. Use um token novo para uma execução independente; compartilhar um token permite cancelar várias buscas. Contadores e incumbente continuam locais à chamada.

A API controlada também observa `Thread.currentThread().isInterrupted()`, preservando o sinal. Não usa `Thread.interrupted()` para limpar o pedido. O sinal não altera o token. O token da geração continua sendo outro tipo, com outro ciclo de vida; cancelar geração não cria estado de solver, e cancelar busca não cancela geração.

| Situação observada | Resultado |
| --- | --- |
| Falha real durante preparação, busca, leitura de relógio ou validação de conclusão | `ERRO`, com classe/mensagem |
| Checkpoint válido detecta token cancelado ou interrupção da thread | `CANCELADO` |
| Sem cancelamento, prazo atingido | `LIMITE_ATINGIDO`, motivo principal tempo |
| Sem cancelamento/prazo atingido, tentativa de admitir nó além do orçamento | `LIMITE_ATINGIDO`, motivo principal nós |
| Busca termina naturalmente, último checkpoint permite continuar e cobertura/validação têm sucesso | `CONCLUIDO` |

A coleta de condições do checkpoint precisa ter sucesso antes de decidir um terminal de controle: uma falha real no relógio ou no observador interno não vira cancelamento/limite. Quando a coleta tem sucesso, a precedência é **cancelamento → tempo → nós**, e o diagnóstico enumera todas as condições observadas naquele checkpoint. O orçamento de nós só é uma condição em pontos que tentam admitir outro estado.

O primeiro terminal escolhido permanece. Um overflow observado antes de cancelamento/prazo posterior continua `ERRO`. Um cancelamento observado antes da conclusão continua `CANCELADO`, mesmo se todas as folhas já tiverem sido cobertas. Um pedido posterior ao último checkpoint pode coincidir com a conclusão; após resultado concluído, cancelar o token não o modifica.

Falhas secundárias ao consolidar métricas, medir duração ou validar o incumbente são acrescentadas ao diagnóstico do terminal já escolhido. Métricas indisponíveis ficam ausentes; um incumbente que falhe na validação é omitido. Isso não converte uma falha primária da busca em controle normal. `Error` da JVM, inclusive `OutOfMemoryError` e `StackOverflowError`, continua sendo propagado.

## Incumbente e cobertura parcial

Somente um ciclo inteiro cujo custo inclua o retorno e seja validado contra `TspProblem` pode aparecer em `TspResult`. Antes da primeira solução, rota e custo ficam ausentes. Zero e `Long.MAX_VALUE` são custos válidos. O incumbente anterior permanece quando o próximo ciclo é interrompido ou falha; caminho parcial nunca é publicado.

`CANCELADO`, `LIMITE_ATINGIDO` e `ERRO` sempre têm `optimalityGuaranteed() == false`. Uma rota pode de fato ser ótima sem existir prova de conclusão.

As métricas mantêm suas definições anteriores. Uma permutação interrompida pode contar como visitada sem avaliação completa. `discardedRoutes` soma exclusivamente podas registradas no histograma original; ramos não explorados não entram nessa contagem. A igualdade `avaliadas + descartadas = (N-1)!` só é obrigatória em `CONCLUIDO`.

TSP-13, matriz manual zero N=25:

| Nós permitidos | Estado | Avaliadas | Descartadas | Ótimo comprovado |
| --- | --- | --- | --- | --- |
| 24 | LIMITE_ATINGIDO | 0 | 0 | Não |
| 250 | LIMITE_ATINGIDO | 1 | 817455074747351039999 | Não |
| 300 | LIMITE_ATINGIDO | 1 | 594596384994354462719999 | Não |
| 301 | CONCLUIDO | 1 | 620448401733239439359999 | Sim |

Os descartes são monotônicos e superam `long` com poucas centenas de estados. Só na conclusão somam com a avaliação **24! = 620448401733239439360000**. A regra de poda e o histograma não foram alterados.

## Preflight e segurança operacional

`SolverPreflight.analyze(algorithm, n, options)` aceita metadados, permitindo recusar uma solicitação antes de gerar matriz/cidades. Informa algoritmo, N, origem 0, opções efetivas, espaço teórico, exatidão do espaço, faixa recomendada, necessidade de confirmação e permissão de execução. Avisos e opções são imutáveis.

O cálculo fatorial do relatório mantém um resultado de até **256 bits**. Quando a próxima multiplicação ultrapassaria esse orçamento de apresentação, para e retorna o produto já demonstrado como **limite inferior**, com `exactSearchSpace() == false`. Isso limita o trabalho a poucas dezenas de multiplicações mesmo para `Integer.MAX_VALUE`. Não é aproximação e não é um N máximo de execução. N=25 tem espaço exato; solicitações gigantes recebem aviso e diagnóstico `>=`, explicitando que o espaço exato não foi calculado. `SearchSpace.completeRoutes` continua disponível para cálculos exatos deliberados.

Com a política padrão, `unlimited()` é permitido até N=10 para cada solver. Acima dessa faixa, a sobrecarga controlada exige pelo menos um limite mesmo com `allowUnlimited=true`. Com limite ativo, N=25 e valores maiores não são rejeitados apenas pelo tamanho. `SolverExecutionPolicy` permite reconfigurar as faixas por algoritmo e exige identificação textual da evidência; cabe ao operador medi-las no ambiente/dataset de destino.

`requiresConfirmation` é informação para o consumidor de apresentação, conforme DOC 02 §9. Uma futura CLI/UI deve apresentar o relatório e obter confirmação fora da faixa. O núcleo não abre diálogos; ele impõe a exigência de orçamento. A CLI atual mantém seu preflight da bateria e usa a assinatura legada, com seu orçamento total existente; sua adoção dos novos controles poderá ser feita depois, passando opções/token por execução e preservando orçamento e medianas da bateria.

## Benchmark que sustenta o perfil

Executado em 17/10/2026, Fedora 44/Linux amd64, Temurin 21.0.12.1, Maven 3.9.11. O experimento usa o mesmo `TspProblem` em dois aquecimentos e cinco medições por configuração, seeds **0, 1 e 657**, geração fora da medição, sem eventos/listeners gráficos. Ambos os limites ficam ativos por chamada: **1.000.000 nós e 2000 ms**. A duração é a interna do solver. O programa para no primeiro N em que alguma seed/amostra não conclui, após terminar as três seeds desse N. Medianas só são publicadas para configurações cujas chamadas todas concluíram.

Evidência integral: [benchmarks/issue-9-linux.txt](benchmarks/issue-9-linux.txt). Valores abaixo são faixas das medianas das três seeds, não promessa para outra máquina nem média entre seeds.

| Solver | N | Nós observados | Medianas (ms) | Estado |
| --- | --- | --- | --- | --- |
| Força bruta | 8 | 5040 | 1,253–1,327 | CONCLUIDO |
| Backtracking | 8 | 7151–9098 | 0,911–1,356 | CONCLUIDO |
| Força bruta | 10 | 362880 | 105,752–106,194 | CONCLUIDO |
| Backtracking | 10 | 228561–292430 | 32,557–39,292 | CONCLUIDO |
| Força bruta | 11 | 1000000 | Não publicada para execução parcial | LIMITE_ATINGIDO por nós |
| Backtracking | 11 | 1000000 | Não publicada para execução parcial | LIMITE_ATINGIDO por nós |

**N=10** é o maior N da faixa contínua medida que concluiu em todas as seeds/amostras para ambos os solvers nesse orçamento. Isso fundamenta `demonstration()`, sem afirmar que N maior nunca conclui ou que toda matriz N=10 conclui em 2000 ms. Contadores dos dois algoritmos descrevem unidades distintas; compará-los não mede diretamente uma mesma quantidade de trabalho.

Para o futuro Modo Aula, recomenda-se inicialmente **N<=8** nessa amostra: no máximo 9098 estados observados, com cota conservadora de até duas ocorrências por estado para os eventos limitados de entrada e poda/avaliação, abaixo de 100 mil. Em N=9 o backtracking já chega a 51917 estados, cuja cota supera 100 mil. A gravação/playback ainda não foi implementada; esta é uma recomendação operacional baseada nas medições, a revalidar com seu custo real.

Reprodução sem interface gráfica, da raiz (o programa também funciona no Windows):

```bash
mvn -pl tsp-core test
java --class-path tsp-core/target/classes tools/SolverExecutionBenchmark.java
java --class-path tsp-core/target/classes tools/SolverExecutionBenchmark.java --examples
```

## Validação

`mvn -pl tsp-core test`: **1620 testes**. `mvn clean verify`: **1749 testes** (1620 núcleo, 125 CLI, quatro app), sem falhas, erros ou ignorados. Os 1630 casos anteriores permanecem; 119 casos novos incluem limites inclusivos, tempo exato com relógio controlado, origem negativa/wrap, incumbentes, estados, condições simultâneas, N=25 e recusa segura de N extremo.

Testes entre threads usam `CountDownLatch` para ordenar cancelamento/interrupção antes da finalização e cancelamento depois da conclusão. Os timeouts são apenas proteção contra travamento; o resultado não depende de velocidade nem `Thread.sleep`. As 30 configurações de N=3..7/seeds 0/1/657 comparam chamadas legadas, limitadas e explicitamente sem limites. Os testes antigos de instrumentação permanecem intactos, inclusive duas leituras do relógio legado.

`clean verify` foi executado sem DISPLAY/WAYLAND_DISPLAY, incluindo o JAR da CLI sem JavaFX. CI Ubuntu/Windows e revisão são acompanhados na PR exclusiva com `Refs #9`, sem merge ou encerramento automático da issue.
