# Auditoria dos algoritmos antes da apresentação

Revisão de **20/10/2026**, sobre os contratos incorporados pela PR #36 (`627de81f`). A camada didática não modifica `tsp-core`. Esta revisão complementa [AUDITORIA_OTIMO.md](AUDITORIA_OTIMO.md) e [CAMINHOS_MINIMOS.md](CAMINHOS_MINIMOS.md), preservando o histórico.

## Força Bruta

`BruteForceSolver` inicializa a permutação 1…N−1 e avança por sucessor lexicográfico. Com origem 0 fixa, enumera exatamente (N−1)! ciclos, inclusive inversos; não deduplica durante a busca. `cycleCost` soma as N arestas da matriz, incluindo a última cidade → 0. Só um custo estritamente menor substitui a melhor rota. A primeira ótima da ordem lexicográfica tem orientação canônica no grafo simétrico; a saída ainda passa por `canonical()` e validação. A contagem de cobertura completa é conferida antes de CONCLUIDO.

Tempo O(N·(N−1)!), espaço auxiliar O(N), além da matriz. Uma permutação iniciada conta como nó; ciclo completo calculado conta como rota avaliada. Nenhum resultado interrompido declara ótimo.

## Backtracking

`BacktrackingSolver.Search.visit` explora candidatos 1…N−1 em ordem crescente, marcando/desmarcando visitas. Com custos não negativos, qualquer conclusão de um prefixo custa pelo menos seu custo parcial. Portanto `partialCost >= bestCost`, havendo incumbente, elimina somente conclusões que não melhoram a melhor solução já encontrada. A igualdade também é segura para o desempate: a primeira ótima lexicográfica é preservada, pois a busca anterior já tem prioridade sobre continuações posteriores. O retorno à origem é somado na folha; sem incumbente nenhuma poda por custo é aplicada.

O histograma conta (N−depth)! folhas por poda. Em conclusão, rotas avaliadas + folhas descartadas = (N−1)! é verificado em `BigInteger`. Nós contam entradas recursivas, sem equivalência direta aos nós da Força Bruta. Tempo no pior caso O(N·(N−1)!), espaço/pilha O(N); os limites operacionais continuam necessários.

## Dijkstra clássico

`DijkstraShortestPathSolver` trabalha com `RefugeGraph` e topologia não direcionada, sem máscaras TSP. Cada aresta usa exatamente `problem.cost(u,v)`, validado não negativo. O heap indexado mantém uma entrada por vértice e extrai a menor distância `BigInteger`, desempate por índice; a adjacência é crescente. Um vértice é fixado definitivamente antes de suas arestas serem examinadas. Pela propriedade dos pesos não negativos, nenhum caminho por vértice ainda não fixado pode produzir valor estritamente menor para o vértice mínimo retirado.

Só rótulos de vértices não fixados são relaxados, e apenas melhora estrita troca predecessor. Todo predecessor de um vértice confirmado possui ordem de fixação menor, logo não há ciclos de predecessores, inclusive com pesos zero. A política é determinística; **não promete caminho lexicograficamente mínimo**. Reconstrução chega à origem, sem retorno artificial. Tempo O((V+E)log V) operações aritméticas, com custo adicional da aritmética `BigInteger`; memória auxiliar O(V), excluindo matriz/topologia/resultado.

`ShortestPathResult.create` publica somente distâncias fixadas, confere predecessor, disponibilidade da aresta, soma, origem e ordem de fixação. Distâncias tentativas são descartadas. Sem distância confirmada, INALCANCAVEL depende de CONCLUIDO; em interrupção o vértice continua NAO_RESOLVIDO. Um destino já confirmado permanece válido mesmo que outros vértices não tenham sido resolvidos. As fábricas validam consistência/viabilidade; a prova de minimalidade depende da execução correta do solver.

## Interrupções, valores extremos e validação

- TSP usa `CostMath.addExact` e `long`, com presença explícita de incumbente. `Long.MAX_VALUE` é legítimo. Overflow é ERRO, sem wrap e sem certificado de ótimo; a política é conservadora e pode interromper ao avaliar uma rota que excede `long`, mesmo que outra representável exista. Esta entrega não altera essa política. Uma incumbente anterior completa e válida pode ser preservada.
- Dijkstra soma/resulta em `BigInteger`, inclusive acima de `Long.MAX_VALUE`; ausência é explícita. Não existe sentinela numérica de infinito.
- Tokens, relógios e orçamentos são verificados nos checkpoints existentes. Cancelamento/limite não transformam uma busca parcial em conclusão. O controller preserva respostas válidas canceladas e distingue preparação de execução. Um cancelamento tardio não reclassifica retroativamente um resultado já concluído.
- `RouteValidator`/`TspRoute` exigem ciclo completo, índices sem repetição interna e retorno à origem, somando N arestas originais. `TspResult` exige rota e custo ambos presentes/ausentes, valida custo recalculado e canonicidade em CONCLUIDO. Validar uma rota ou decompor sua soma não prova seu ótimo independentemente.
- A falha interna de validação de Dijkstra é ERRO mesmo após CANCELADO/LIMITE_ATINGIDO (`finishResult`), sem publicação de distâncias inválidas. Os testes existentes dessa correção são conservados.
- Solvers/resultados não conhecem JavaFX, projeção, terreno, filtragem ou reprodução. A nova camada deriva dados dos resultados, sem enviar modificações de volta ao núcleo.

## Referência acadêmica TSP

**“Aula 5 - Caxeiro Viajante.pdf”, página 4**, mapeamento v1=0, v2=1, v3=2, v4=3. Matriz e página foram fornecidas no requisito pelo responsável acadêmico; os PDFs originais não estão versionados nem foram localizados nesta revisão. Não se afirma inspeção direta do arquivo.

```text
0 1 4 6
1 0 2 3
4 2 0 3
6 3 3 0
```

`AcademicTspExampleTest` executa os dois solvers reais. Força Bruta conclui e avalia **seis ciclos completos**; Backtracking conclui com mesmo custo/ciclo. Saída canônica **0–1–3–2–0**, custo **11**. Tabela esperada:

| Passo | Conexão | Peso | Acumulado |
| --- | --- | --- | --- |
| 1 | 0 → 1 | 1 | 1 |
| 2 | 1 → 3 | 3 | 4 |
| 3 | 3 → 2 | 3 | 7 |
| 4 | 2 → 0 (retorno) | 4 | 11 |

Expressão: **1 + 3 + 3 + 4 = 11**. A matriz manual não é recalculada a partir das coordenadas de apresentação.

## Referência acadêmica Dijkstra e escopo

Segundo os dados fornecidos para **“6-AlgDijkstra.pdf”, páginas 66–67**, o exercício usa um **grafo direcionado**, com caminho **0–2–3** e custo **9**. O NodeWalker atual usa `RefugeTopology` não direcionada e `TspProblem` simétrico. Portanto ele **não reproduz fielmente esse grafo orientado**. Não foram criadas arestas reversas nem adaptado o exemplo silenciosamente. O dado é registrado como referência para [uma proposta posterior](PROPOSTA_GRAFOS_DIRECIONADOS.md), não como teste aprovado desse exemplo na implementação atual.

## Evidência independente

A suíte existente mantém comparações TSP com Held–Karp de teste em `BigInteger` e caminhos mínimos com Floyd–Warshall independente. Nenhum desses oráculos passa a algoritmo de produção. Os novos testes de apresentação conferem todos os termos, zero, `Long.MAX_VALUE`, distância 2·`Long.MAX_VALUE`, interrupções, ausência de incumbente, destinos não resolvidos/inalcançáveis, topologias, reprodução, cache de destino e invalidação sob filas controladas. Os testes não executam Força Bruta em N grande. Nenhum defeito que exigisse reescrita dos algoritmos foi encontrado nesta revisão.

## Auditoria final da #13 — 21/10/2026

Nova leitura dos três solvers, validadores e resultados na main 9c23bb6 e nova execução dos **2373 testes**, incluindo todos os oráculos independentes. Confirmados enumeração dos ciclos, retorno, poda não negativa, canonicidade TSP, heap mínimo Dijkstra, predecessores sem ciclos, confirmação versus rótulo provisório, limites, interrupções e política de overflow já descritos. Não se encontrou defeito matemático reproduzível; nenhum solver/contrato foi alterado.

O exemplo acadêmico BF avalia seis ciclos e produz 11, ciclo 0–1–3–2–0, soma 1+3+3+4; BT conclui com o mesmo ótimo e quatro ciclos avaliados. [Saída atual, incluindo MAX/BigInteger e resultados limitados](evidencias/issue-13/academic-reference.txt). Os JARs core/CLI não contêm HeldKarpOracle/FloydWarshallOracle. [Stacks observadas no debugger](evidencias/issue-13/threads.json) confirmam geração/solvers no worker e preparação geométrica em outro worker, todos fora da FX Application Thread; esse ensaio não mede desempenho.

[Matriz de aceite e pendências](VALIDACAO_MVP.md) · [benchmark com orçamento prévio](benchmarks/issue-13/README.md). CI headless, automação gráfica e interação humana são evidências distintas. O exercício Dijkstra dirigido não foi declarado suportado.
