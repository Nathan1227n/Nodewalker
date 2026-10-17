# Auditoria independente do ótimo — issue #10

O `tsp-core` possui uma referência Held–Karp exclusivamente em `src/test/java`. Não é um terceiro solver do produto, não implementa `TspSolver` e não é incluído nos JARs da aplicação/CLI. Nenhum arquivo de produção, dependência, algoritmo, controle de execução ou regra matemática foi alterado nesta entrega.

Fontes: issue #10 e DOC 03 §17, TSP-01 até TSP-13. A implementação parte da `main` em `2056fd7`, merge da PR #31. Os 1749 casos anteriores foram mantidos; a bateria diferencial existente foi enriquecida, preservando seus parâmetros.

## Referência matemática independente

`HeldKarpOracle` recebe um `TspProblem` válido e lê somente `size()` e `cost(i,j)`. Calcula a referência com sua própria programação dinâmica iterativa e aritmética `BigInteger`. Não chama os solvers, `CostMath`, `RouteValidator`, `SearchSpace`, histograma ou contadores para calcular custo ou rota. O resultado próprio `Optimum` contém custo matemático, lista imutável de índices e uma indicação opcional de custo representável em `long`.

O subconjunto S contém cidades ainda não visitadas, entre 1 e N−1. O bit u−1 representa a cidade u; a origem 0 nunca pertence à máscara.

```text
D(v, vazio) = custo(v, 0)
D(v, S) = min { custo(v,u) + D(u, S sem u) : u pertence a S }
ótimo = D(0, {1, ..., N−1})
```

Cada ciclo que sai de v escolhe alguma primeira cidade u e depois resolve o subproblema restante; reciprocamente, cada escolha e sua continuação formam uma solução viável. Isso fundamenta a recorrência por indução no tamanho de S. A base inclui a aresta de retorno à origem. Remover um bit diminui o valor numérico da máscara, permitindo preenchimento crescente sem recursão. Estados em que a cidade atual ainda aparece em S não são usados.

Os custos são somados como `BigInteger` desde a leitura das arestas. Não há infinito representado por `Long.MAX_VALUE`, saturação, truncamento ou soma intermediária em `long`. A origem do dado é a mesma matriz imutável usada pelos solvers; o oráculo não audita a implementação dessa matriz ou do gerador, já cobertos por testes específicos.

## Desempate e orientação

A DP armazena somente custos, sem guardar o primeiro predecessor encontrado. Seus candidatos são examinados em ordem **decrescente**, deliberadamente distinta da reconstrução.

Na reconstrução, partindo de 0, examinam-se explicitamente os índices **crescentes** e escolhe-se o primeiro u para o qual:

```text
custo(v,u) + D(u, S sem u) == D(v,S)
```

Ao menos uma escolha satisfaz a igualdade pela recorrência. O menor índice entre essas escolhas produz o menor próximo elemento entre todas as continuações ótimas. Aplicando o mesmo argumento ao sufixo, obtém-se a primeira rota ótima lexicográfica inteira. O desempate não depende da ordem de preenchimento da DP nem da iteração de mapas/conjuntos.

Em uma matriz simétrica, o inverso de um ciclo também é ótimo. Se o primeiro ciclo lexicográfico tivesse `route[1] > route[N−1]`, seu inverso seria lexicograficamente menor, uma contradição. Portanto a referência já é canônica, sem chamar a canonicalização de produção. Os testes verificam empate total zero/positivo, ciclos distintos empatados, ótimo encontrado depois da primeira permutação, custo do ciclo inverso e sequência literal esperada.

## Limite e custo computacional

O teto explícito do oráculo é **N=12**, verificado antes do deslocamento de bits e da alocação exponencial. N=13 é recusado em teste. Esse teto é restrito à infraestrutura de testes e não limita os solvers do produto.

- Tempo: O(N²·2^(N−1)); memória: O(N·2^(N−1)), além de custos `BigInteger` e matriz N².
- N=12: até **2048 máscaras × 12 posições = 24576 células** de tabela. Não se guardam rotas completas em cada estado.
- O teste da fronteira N=12 executa **somente Held–Karp**. Usa um anel manual: todas as arestas custam pelo menos 1; o ciclo ascendente e seu inverso usam apenas arestas de custo 1. O limite inferior e uma testemunha provam ótimo 12 e a rota lexicográfica conhecida.
- Os testes diferenciais permanecem em **N=3..7**, com 200 seeds consecutivas (0..199), −1, `Long.MIN_VALUE`, `Long.MAX_VALUE` e 657: **204 seeds × 5 tamanhos = 1020 configurações**. Cada problema já comparava os dois solvers; agora recebe também uma chamada do oráculo.
- N=25 **nunca é enviado ao Held–Karp ou à força bruta**. Seus testes de solver usam somente backtracking com orçamento de nós.

As instâncias geradas pequenas têm custos cujos ciclos cabem em `long`; nelas ambos os solvers devem concluir e coincidir exatamente com o oráculo em custo e rota. A bateria não foi duplicada em outra classe.

## Ótimo matemático, representabilidade e execução concreta

São três afirmações distintas: conhecer o mínimo matemático, saber se esse mínimo cabe em `long` e observar que uma execução do solver terminou corretamente. A auditoria exige `CONCLUIDO` antes de comparar um resultado como prova de ótimo. Concordância de dois estados `ERRO` não satisfaz essa exigência.

| Caso dirigido | Referência matemática | Execução concreta esperada |
| --- | --- | --- |
| Triângulo com uma aresta `Long.MAX_VALUE` e as outras zero | `Long.MAX_VALUE`, representável | Ambos concluem e coincidem com a referência |
| Triângulo com arestas `Long.MAX_VALUE`, 1 e 0 | 9223372036854775808, fora de `long` | Ambos retornam ERRO, sem incumbente |
| Matriz N=4, aresta 0–1=`Long.MAX_VALUE`, demais arestas 1 | Ótimo 4, rota 0–2–1–3–0 | Ambos observam overflow no primeiro ramo e retornam ERRO antes do ótimo |
| Matriz N=4 com primeiro ciclo de custo máximo e ciclo posterior de custo 1 | Ótimo 1, representável | Ambos retornam ERRO após overflow posterior, preservando incumbente `Long.MAX_VALUE` |
| Matriz com ótimo 4 e retorno excedente atrás de um prefixo já podado | Ótimo 4, representável | Backtracking conclui; força bruta observa overflow e retorna ERRO |

Nesses casos de erro, o oráculo continua sendo referência matemática; não exige ignorar uma falha aritmética observada nem transforma o incumbente em ótimo comprovado. A aritmética da DP também é testada com custo 3×`Long.MAX_VALUE` = 27670116110564327421.

Asserções externas podem usar os contratos existentes. Além disso, `SolverAuditAssertions` confere independentemente a sequência completa, unicidade/faixa dos índices e soma `BigInteger` das N arestas. Em resultados parciais, um incumbente deve ser válido e ter custo maior ou igual ao mínimo matemático, sem precisar ser ótimo ou canônico.

## Cobertura TSP-01 a TSP-13

| Evidência | Cobertura e contribuição desta auditoria |
| --- | --- |
| TSP-01 | Triângulo de custo 45 e rota literal, incluindo retorno; validação do oráculo e de ambos os solvers |
| TSP-02 | Matriz do DOC 03 §7: custo 300, primeira rota 0–1–2–3–0 |
| TSP-03/04 | Empates totais zero/positivos, inversão e ciclos distintos; DP em ordem oposta à reconstrução |
| TSP-05/06 | Ciclo fechado/custo rechecados, orientação, lista imutável, referência seed 657 e repetição; contadores comparados separadamente da duração |
| TSP-07 | As 1020 configurações existentes agora comparam Held–Karp, força bruta e backtracking |
| TSP-08 | Invariante em cada comparação concluída; família zero N=5..12 já existente; partição explícita de folhas eliminadas por prefixos conhecidos |
| TSP-09/10 | Testes anteriores preservados: relógios controlados, barreiras entre threads, sinal de interrupção e fronteiras K−1/K/K+1. Novos casos interrompem os dois solvers com incumbente **20**, embora o ótimo independente seja **4** |
| TSP-11 | Referências fixas e mesma bateria em Ubuntu/Windows; geração determinística e seus testes permanecem intactos |
| TSP-12 | DP independente só em testes, guardada em N≤12; teste N=12 sem força bruta |
| TSP-13 | N=25 zero, somente backtracking controlado; estados, cobertura parcial e descartes exatos por fórmula analítica |

Para TSP-08, a matriz de quatro cidades com diagonais caras tem primeira rota ótima 01230, custo 4. Os prefixos 013, 02 e 031 têm custo parcial >=4, enquanto seus ancestrais têm custo menor. Seus conjuntos explícitos de folhas são `{01320}`, `{02130,02310}` e `{03120}`: quatro folhas disjuntas, com cardinalidades 1!, 2! e 1!. A auditoria verifica esses prefixos, ciclos e cardinalidades sem consultar o histograma, e confronta com dois ciclos avaliados, quatro descartados e dez estados reportados. Isso evita validar descartes apenas como complemento algébrico do total. Não é uma captura de todos os eventos de poda das 1020 instâncias.

## Referência analítica N=25

Na matriz zero, as primeiras 25 entradas percorrem o primeiro ciclo. A partir dele, cada irmão restante é podado imediatamente por igualdade. Há r podas com r cidades restantes, para r=1..23, cada uma eliminando r! folhas. Portanto:

```text
operações de poda = 1+2+...+23 = 276
nós para concluir = 25+276 = 301
descartadas = soma(r*r!, r=1..23) = 24!−1
24! = 620448401733239439360000
```

Para orçamento K, antes da primeira rota os descartes são zero. Depois dela, sejam `p=min(K,301)−25` podas observadas, r tal que `r(r−1)/2 <= p < r(r+1)/2` e `t=p−r(r−1)/2`. A soma parcial é **(t+1)·r!−1**. O esperado é calculado a partir de K, sem usar os contadores reportados para gerar a referência.

O teste existente agora confere essa fórmula em K=24/25/100/250/300/301/302, além de manter as verificações de monotonicidade, incumbente e estado. K=300 retorna LIMITE_ATINGIDO; K=301 e K=302 concluem. Os descartes exatos em K=250 e K=300 são, respectivamente, **817455074747351039999** e **594596384994354462719999**. Só a conclusão exige cobertura completa e permite declarar ótimo.

## Diagnósticos e reprodução

Os diagnósticos são `Supplier<String>` passados às asserções. Só em uma falha são formatados N, seed (ou matriz manual), matriz completa, referência matemática/analítica, custo, rota, estado, garantia declarada, motivo e métricas de cada solver disponível. Em N=25, o diagnóstico inclui somente backtracking e referência analítica. Um teste força e captura uma falha para conferir os campos e comprovar que o fornecedor da referência não é invocado em asserções bem-sucedidas. A execução normal não imprime matrizes.

Da raiz, Java 21 e Maven:

```bash
mvn -pl tsp-core test
mvn clean verify
git diff --check
```

Recortes reproduzíveis:

```bash
mvn -pl tsp-core "-Dtest=HeldKarpOracleTest,IndependentOptimalityAuditTest" test
mvn -pl tsp-core "-Dtest=BacktrackingSolverTest#agreesWithBruteForceAndHeldKarpOnSmallRepresentableInstances" test
mvn -pl tsp-core "-Dtest=HeldKarpOracleTest#maximumTwelveCityInstanceRunsOnlyHeldKarpWithAnAnalyticallyKnownOptimum" test
mvn -pl tsp-core "-Dtest=SolverExecutionControlsTest#tsp13KeepsPartialCoverageSeparateFromCompletionAndDiscardCountsAboveLong" test
```

## Resultados e limites da evidência

São **1652 testes do núcleo e 1781 no reactor completo**, com **32 casos novos** e os 1749 anteriores preservados/enriquecidos. Os 1020 casos diferenciais mantêm a mesma quantidade e as mesmas seeds. Nenhuma divergência de custo ou rota em execuções concluídas foi encontrada; as diferenças de estado nos casos de overflow são previstas e verificadas explicitamente.

No ambiente local Fedora 44/Temurin 21.0.12.1/Maven 3.9.11, o baseline do núcleo levou cerca de 2 s no Maven, com `BacktrackingSolverTest` em 0,376 s. Após a auditoria, a mesma classe levou aproximadamente 0,4 s; as duas classes novas, juntas, aproximadamente 0,05 s. A chamada com recompilação dos testes ficou em cerca de 3,5 s. O `clean verify` completo levou **5,8 s no Maven**; a soma das durações das classes nos relatórios foi **1,615 s** (1,023 s núcleo, 0,564 s CLI, 0,028 s app). Essas duas medidas têm escopos diferentes. Os tempos são observações, sem limiares de aprovação e sem afirmar significância estatística da diferença.

O build completo foi executado sem DISPLAY/WAYLAND_DISPLAY. Os JARs gerados do núcleo e da CLI foram inspecionados: não contêm `HeldKarpOracle`, asserções ou classes da auditoria. `git diff --check` passou; os arquivos de produção, POMs, CLI, UI e workflow permaneceram idênticos à base.

Não há sleeps ou dependência da velocidade da máquina para limites/cancelamento. Os timeouts existentes são proteção contra travamento, não condição de sucesso. `elapsedNanos` aparece no diagnóstico e é verificado como não negativo; a auditoria não exige igualdade entre execuções. A evidência cobre os casos escolhidos e a faixa explícita do oráculo, sem constituir prova universal para todas as matrizes. CI Ubuntu/Windows e revisão são acompanhados na PR exclusiva com `Refs #10`, sem merge nem fechamento automático.
