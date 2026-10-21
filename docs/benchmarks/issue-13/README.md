# Benchmark controlado do MVP — 21/10/2026

Produto: main `9c23bb6bc914b6be563a16e1ad0b2ae1cfe0d80c`. Ambiente: Fedora 44, Temurin 21.0.12.1, Ryzen 7 5700X, 8 núcleos/16 threads, RAM 31 GiB reportados. [Inventário completo](../../evidencias/issue-13/environment.json). A bateria foi executada sem os roteiros gráficos em paralelo.

```bash
mvn clean verify
java --class-path tsp-core/target/classes tools/validation/MvpBenchmark.java
```

[Comando e exit code](command.json) · [Todas as configurações, aquecimentos, amostras e métricas](samples.jsonl) · [Fonte do ensaio](../../../tools/validation/MvpBenchmark.java).

## Método e orçamento definidos antes das chamadas

São **duas chamadas de aquecimento e cinco medições por configuração**, uma JVM e execução sequencial. As 36 configurações produziram **252 chamadas (72 aquecimentos e 180 medidas)**, todas CONCLUIDO. Não há resultado cancelado/limitado misturado às medianas. A bateria exige sete respostas concluídas e determinísticas antes de emitir o resumo de cada configuração; confere a matriz depois das buscas.

- TSP: N=3/5/8/10, seeds=0/1/657, Força Bruta e Backtracking. Cada chamada tem teto de **1.000.000 nós e 2000 ms**. N=10 na Força Bruta tem **362880 ciclos completos por chamada**; não se executou Força Bruta acima de N=10. Os dois algoritmos recebem exatamente o **mesmo objeto TspProblem**, não duas gerações presumidamente iguais. Custo e ciclo devem coincidir; o hash da matriz fica no JSONL.
- Caminhos mínimos: N=8/32/128, seeds=0/657, completo e esparso conectado. Mesma matriz, mas grafos diferentes. Origem0, destinoN−1; no esparso, seed da topologia igual à da instância e extras=N/2. Teto de **10.000 vértices, 1.000.000 entradas e 2000 ms** por chamada. Esses contadores não são nós/folhas TSP.
- Bateria: orçamento de **60 segundos**, conferido entre chamadas; limites cooperativos de cada chamada continuam ativos. Não é um timeout preemptivo de heap/JVM. Tempo externo do comando: **3,465 s**; parede interna da bateria: **3,091 s**. Compilação do source launcher/startup não entra no tempo interno dos solvers.

A coluna de tempo abaixo é a **mediana de cinco `metrics.elapsedNanos` internos**, convertida em milissegundos com seis casas. Não inclui geração, topologia, apresentação, impressão nem conferências posteriores. Geração/topologia têm campos separados no JSONL. A CLI do README mede outra fronteira (chamada externa de solve); não confundir as duas séries.

Dois aquecimentos são curtos: JIT, GC, escalonamento e ruído podem afetar os resultados, especialmente os muito pequenos. Não há significância estatística, ranking universal, teste de carga ou promessa para outro hardware. Não se mediu FPS apresentado, heap estabilizado ou consumo máximo de memória.

## TSP — mesma matriz por par

Todos os pares concluíram com custo e ciclo iguais. Nós de BF contam permutações iniciadas; nós de BT contam entradas recursivas, incluindo podas. Comparar os contadores como se medissem a mesma operação seria incorreto. Rotas e todos os contadores estão nas amostras brutas; custos abaixo são da primeira amostra medida, iguais nas sete chamadas.

| N | Seed | Algoritmo | Custo | Ciclos avaliados | Mediana interna (ms) |
| --- | --- | --- | --- | --- | --- |
| 3 | 0 | Força Bruta | 2811 | 2 | 0.030321 |
| 3 | 0 | Backtracking | 2811 | 2 | 0.025200 |
| 3 | 1 | Força Bruta | 2549 | 2 | 0.031240 |
| 3 | 1 | Backtracking | 2549 | 2 | 0.026010 |
| 3 | 657 | Força Bruta | 1994 | 2 | 0.019580 |
| 3 | 657 | Backtracking | 1994 | 2 | 0.017740 |
| 5 | 0 | Força Bruta | 3104 | 24 | 0.074800 |
| 5 | 0 | Backtracking | 3104 | 21 | 0.066230 |
| 5 | 1 | Força Bruta | 3520 | 24 | 0.042600 |
| 5 | 1 | Backtracking | 3520 | 14 | 0.058260 |
| 5 | 657 | Força Bruta | 2845 | 24 | 0.043270 |
| 5 | 657 | Backtracking | 2845 | 17 | 0.048091 |
| 8 | 0 | Força Bruta | 4148 | 5040 | 1.677007 |
| 8 | 0 | Backtracking | 4148 | 372 | 1.371136 |
| 8 | 1 | Força Bruta | 4030 | 5040 | 1.678907 |
| 8 | 1 | Backtracking | 4030 | 214 | 1.099294 |
| 8 | 657 | Força Bruta | 3574 | 5040 | 1.266836 |
| 8 | 657 | Backtracking | 3574 | 176 | 0.923324 |
| 10 | 0 | Força Bruta | 4425 | 362880 | 105.671606 |
| 10 | 0 | Backtracking | 4425 | 1509 | 33.834179 |
| 10 | 1 | Força Bruta | 4303 | 362880 | 105.620986 |
| 10 | 1 | Backtracking | 4303 | 1984 | 38.151838 |
| 10 | 657 | Força Bruta | 3618 | 362880 | 105.642045 |
| 10 | 657 | Backtracking | 3618 | 987 | 28.639646 |

## Dijkstra clássico — problemas de caminhos mínimos

Cada linha refere-se ao grafo/origem/destino indicado, sem comparação de custo com TSP. Restringir conexões pode aumentar distâncias; o esparso faz menos trabalho de exame de adjacência, mas não é o mesmo problema do completo. Os tempos muito curtos variam com aquecimento; o caso N128/seed0 completo mostra essa variação e não foi removido.

| N | Seed | Topologia | Conexões | Distância a N−1 | Vértices processados | Entradas examinadas | Mediana interna (ms) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 8 | 0 | Completa | 28 | 974 | 8 | 56 | 0.050250 |
| 8 | 0 | Esparsa | 11 | 974 | 8 | 22 | 0.014840 |
| 8 | 657 | Completa | 28 | 616 | 8 | 56 | 0.022990 |
| 8 | 657 | Esparsa | 11 | 616 | 8 | 22 | 0.014440 |
| 32 | 0 | Completa | 496 | 395 | 32 | 992 | 0.234971 |
| 32 | 0 | Esparsa | 47 | 3119 | 32 | 94 | 0.037350 |
| 32 | 657 | Completa | 496 | 371 | 32 | 992 | 0.217411 |
| 32 | 657 | Esparsa | 47 | 7802 | 32 | 94 | 0.031770 |
| 128 | 0 | Completa | 8128 | 421 | 128 | 16256 | 1.682688 |
| 128 | 0 | Esparsa | 191 | 1558 | 128 | 382 | 0.029140 |
| 128 | 657 | Completa | 8128 | 650 | 128 | 16256 | 0.695413 |
| 128 | 657 | Esparsa | 191 | 19761 | 128 | 382 | 0.030940 |

As métricas adicionais registradas são tentativas/êxitos de relaxamento e pico da fila, com seus significados próprios. Não se inventou contador de poda para Dijkstra. A distância é serializada como texto decimal para preservar BigInteger sem perda. As matrizes geradas aqui têm pesos pequenos; os casos MAX/acima de long são auditados separadamente na [referência acadêmica e de extremos](../../evidencias/issue-13/academic-reference.txt) e na suíte, não apresentados como este benchmark de geração.
