# Revisão técnica das fontes — 11/10/2026

## Referências normativas

1. DOC 01 — Visão, Objetivos e Escopo, v0.4 (06/10/2026): escopo, prioridades P0–P3 e gates G0–G5.
2. DOC 02 — Requisitos do Sistema, v0.4 (06/10/2026): RF-01 a RF-22, RNF, estados, geração determinística, aceite e evidências.
3. DOC 03 — Especificação do TSP, v0.4 (06/10/2026): algoritmo, custo, contagem de poda, métricas, teste diferencial e arquitetura do núcleo.
4. NodeWalker — visão geral e cronograma, 11/10/2026: nome do produto, repositório, datas e sequência de implementação.

Esta revisão **não altera** os documentos originais. Quando há detalhe impreciso, o item abaixo é identificado como decisão de implementação a validar, não como um requisito já aprovado.

## Decisões que precisam permanecer intactas

- Exatidão do TSP antes da renderização. A visão 3D não modifica custos nem decisões dos solvers.
- Grafo completo e simétrico; custos `long` não negativos; origem fixa no índice 0; N >= 3.
- Força bruta enumera `(N-1)!` permutações lexicográficas, sem poda, e conserva a primeira melhor rota em empate.
- Backtracking percorre índices crescentes e pode podar ao atingir `partialCost >= bestCost`. O resultado declarado CONCLUÍDO tem de ser globalmente ótimo.
- Contar **folhas descartadas**, e não apenas chamadas de poda: histograma por profundidade; soma em `BigInteger`.
- Ao concluir o backtracking, `BigInteger(rotasCompletasAvaliadas) + rotasDescartadas = (N-1)!`.
- Estados de solver e de playback são independentes; cancelamento/limite/erro jamais equivalem à conclusão.
- Listener vazio no benchmark; separar cronometragem do solver e renderização.

## Pontos de atenção para implementação

1. **Coordenadas:** o DOC 03 §3 menciona coordenadas `double` como representação geométrica; DOC 02 §12 e DOC 03 §18 especificam que o **gerador armazena inteiros** entre 0 e 1000. Na issue #2, `City` foi definido como record com coordenadas `int`, convertidas apenas para a geometria JavaFX. O modelo geral aceita todo o intervalo de `int`, inclusive coordenadas negativas; o intervalo [0, 1000], a separação mínima de 50 e a geração determinística pertencem ao gerador da issue #3, implementado e aguardando revisão. Coordenadas coincidentes são válidas em uma instância manual; não se infere custo a partir delas.
2. **Overflow do custo:** matrizes manuais aceitam todo custo não negativo representável em `long`, inclusive `Long.MAX_VALUE`; a construção não calcula somas nem afirma que todas as rotas cabem em `long`. Na issue #2, `CostMath.addExact` usa `Math.addExact` e lança `ArithmeticException` com mensagem e causa quando a soma excede esse limite; operandos negativos são rejeitados com `IllegalArgumentException`. Não há saturação, truncamento nem uso de `Long.MAX_VALUE` como sentinela de infinito. Os futuros solvers deverão usar essa operação em todas as acumulações e tratar overflow como erro, sem declarar CONCLUÍDO ou ótimo.
3. **Determinismo:** a regra estrita de empate e a ordem dos candidatos devem ser observadas por ambos os solvers. Testar não só custo ótimo como também a mesma **rota** em uma bateria de seeds.
4. **Poda `>=`:** segura pelo caráter não negativo dos pesos. O teste precisa incluir custos zero, vários ótimos e subárvores inteiramente podadas. A contagem por profundidade deve considerar inclusive a poda de uma folha.
5. **Limites:** não inventar um N máximo universal. Medir limite prático em G2, mostrar `(N-1)!` no preflight e exigir confirmação/limites além da faixa recomendada.
6. **Cross-platform:** `Platform.isSupported(ConditionalFeature.SCENE3D)` deve determinar fallback; a UI 2D precisa continuar útil após G3. O CI não prova renderização gráfica.
7. **Risco de cronograma:** como o prazo é 19/10/2026, G4/G5 não são compromissos do MVP e não devem consumir tempo antes de G3 estabilizar.

## Evidências mínimas por fase

- **G0:** `ARCH-01`, `ARCH-02`, `ARCH-05`, 3D capability inicial.
- **G1:** `TSP-01`, `TSP-02`, `TSP-05`, `TSP-06`, `TSP-11`.
- **G2:** `TSP-07` a `TSP-10`, `TSP-12`, `TSP-13`; validar limites e contas `BigInteger`.
- **G3:** `UI-01` a `UI-04`, `3D-01` a `3D-05`, `ARCH-04`.

## Status desta entrega

O G0 já foi publicado e sua demonstração 3D/2D original foi validada pelo desenvolvedor no Fedora. Na correção da issue #1, a chave ausente em `NodeWalkerApp.setPreview` foi restaurada e a seleção inicial foi integrada a `PreviewModeSelector`, uma função independente de JavaFX. Os três testes de `SearchSpaceTest` e os quatro testes de seleção passaram com Java 21 em `mvn clean verify` e `mvn clean install`, incluindo verificação sem variáveis de display.

As Actions v5 foram preservadas com Temurin 21 e matriz Ubuntu/Windows. A execução atual do CI e as evidências gráficas finais são acompanhadas em [ROADMAP.md](ROADMAP.md). Testar a decisão com suporte 3D indicado como indisponível não comprova renderização real nesse ambiente; CI também não comprova abertura da janela no Windows. Nenhum solver de G1/G2 foi implementado.

Os plugins Maven clean, resources, jar e install foram fixados no POM pai nas versões validadas no Fedora, após uma falha real de resolução do plugin clean no CI da PR. Isso evita que instalações Maven diferentes escolham versões implícitas diferentes; a configuração Java 21 e as versões de compiler/surefire foram preservadas.

## Modelo de domínio G1 — issue #2, 12/10/2026

`City`, `TspProblem` e `CostMath` pertencem a `io.github.nathan1227n.nodewalker.core.model`, sem dependências gráficas. O construtor `TspProblem(List<City> cities, long[][] costs)` recebe uma lista ordenada por índice: as posições devem corresponder aos índices 0 até N-1; não reordena entradas. `List` explicita que a ordem e as posições fazem parte do contrato. Exigir `city.index() == posição` garante unicidade e continuidade dos índices e fixa a origem no índice 0. A matriz manual deve ser quadrada N por N, simétrica, não negativa e com diagonal zero, com N >= 3.

O construtor valida uma cópia das cidades e de cada linha da matriz. `cities()` retorna uma lista imutável de records imutáveis; nenhuma API expõe a matriz interna. `size()`, `cost(from, to)` e `originIndex()` completam o contrato. Entradas nulas falham com `NullPointerException`, violações de domínio com `IllegalArgumentException` e índices de acesso inválidos com `IndexOutOfBoundsException`, com mensagens de diagnóstico. Cópias defensivas protegem contra alterações posteriores; não se garante um snapshot atômico de entradas alteradas concorrentemente durante a construção.

`mvn -pl tsp-core test` passou com 66 testes e `mvn clean verify` passou nos três módulos com 70 testes: 4 de City, 47 de TspProblem, 12 de CostMath e os 7 anteriores preservados. Esses números incluem as execuções de testes parametrizados; não houve falhas, erros ou testes ignorados. Isso valida o modelo e a aritmética, sem comprovar critérios de solução ótima dos solvers ainda não implementados. A [PR #19](https://github.com/Nathan1227n/Nodewalker/pull/19) e a correção de `Collection<City>` para `List<City>` na [PR #20](https://github.com/Nathan1227n/Nodewalker/pull/20) foram mescladas na `main` em 12/10/2026. A issue #2 foi encerrada após confirmação do merge, antes da publicação da issue #3. Gerador, custos euclidianos e algoritmos não faziam parte da entrega do modelo.

Após o ajuste para `List<City>`, os dois comandos foram executados novamente no Fedora com Java 21 e mantiveram os mesmos 66/70 testes aprovados, sem falhas, erros ou testes ignorados. O teste com `LinkedHashSet` foi adaptado para verificar a ordem de uma lista; os demais cenários de validação e imutabilidade foram preservados. `git diff --check` passou, sem arquivos `target/` versionados ou dependências JavaFX no núcleo.

## Gerador determinístico G1 — issue #3, 12/10/2026

Fontes conferidas: DOC 02 §12 e DOC 03 §§3 e 18, extraídos dos `.docx` versionados. A API é `TspGenerator.generate(int n, long seed)`, no pacote `io.github.nathan1227n.nodewalker.core.generator`, exclusivamente no `tsp-core`. `n < 3` falha com `IllegalArgumentException`; não foi imposto um N máximo ou um limite de tentativas. O retorno é um `TspProblem` imutável, com índices 0 até N-1 na ordem de aceitação e origem fixa 0. A matriz continua validada e copiada pelo modelo existente.

A [PR #21](https://github.com/Nathan1227n/Nodewalker/pull/21) foi mesclada na `main` em 12/10/2026 (America/Fortaleza), no commit `02eae42`; a issue #3 permanece aberta. Essa integração é o pré-requisito da entrega de contratos abaixo.

O DOC 02 §12 exige a seed armazenada na instância. Para cumprir esse requisito sem remover o construtor manual, `TspProblem` ganhou uma sobrecarga `(List<City>, long[][], long seed)` e `seed()` retorna `OptionalLong`. O construtor manual original retorna uma seed ausente; o gerador preserva qualquer seed `long`, inclusive valores negativos e extremos. Ambos os construtores compartilham as mesmas validações e cópias defensivas. A seed é metadado e não substitui a validação do problema.

Cada geração cria exatamente um `new Random(seed)`. Cada tentativa executa `random.nextInt(0, 1001)` três vezes, em ordem **x, y, z**. Só depois dessas três chamadas o candidato é comparado às cidades já aceitas. Se qualquer distância ao quadrado for **menor que 2500**, o candidato é descartado e o próximo trio é sorteado pelo mesmo `Random`, sem reinicializar seu estado. A distância exatamente 50 é aceita. Não há arredondamento, embaralhamento ou fonte adicional de aleatoriedade nessa decisão.

Para cada par não ordenado, o custo é `Math.round(Math.sqrt(dx*dx + dy*dy + dz*dz))`, armazenado em `long` e atribuído às duas posições simétricas. A diagonal permanece zero. As diferenças são calculadas em `long`; na faixa [0, 1000], cada módulo é no máximo 1000 e a soma dos quadrados é no máximo 3.000.000, sem overflow e exatamente representável em `double`. Os auxiliares geométricos internos se destinam a essa faixa, não ao intervalo completo de coordenadas das matrizes manuais. A renderização JavaFX não participa desses cálculos.

A [documentação de Random no Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Random.html) garante a mesma sequência com a mesma seed e ordem de chamadas, com algoritmos definidos para portabilidade. [Math.sqrt e Math.round](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html) especificam, respectivamente, raiz corretamente arredondada e conversão ao `long` mais próximo. Como os dados de entrada e as operações são os mesmos, coordenadas e custos são reproduzíveis entre plataformas.

### Exemplo de referência com rejeição

```java
TspProblem problem = TspGenerator.generate(4, 657L);
// problem.seed().orElseThrow() == 657L
```

| Índice | x | y | z |
| --- | --- | --- | --- |
| 0 | 930 | 591 | 463 |
| 1 | 188 | 89 | 36 |
| 2 | 284 | 169 | 46 |
| 3 | 232 | 587 | 882 |

Após as cidades 0 e 1, o trio **(936, 637, 445)** é sorteado e rejeitado: sua distância ao quadrado para a cidade 0 é **2476**, inferior a 2500. Arredondar essa distância produziria 50, portanto usar o custo arredondado na rejeição violaria o contrato. Os três sorteios dessa tentativa são consumidos; os dois trios seguintes são (284, 169, 46) e (232, 587, 882), aceitos nessa ordem.

```text
       0    1    2    3
0      0  992  877  814
1    992    0  125  983
2    877  125    0  936
3    814  983  936    0
```

Esses valores são literais em `TspGeneratorTest`, não resultados calculados pelo gerador durante o teste. As referências foram preparadas independentemente em Python, usando o LCG de 48 bits especificado para `Random` e raízes `Decimal` com 50 dígitos. O teste também confere o fluxo bruto de cinco trios do `Random`, sem replicar a seleção de cidades. Uma segunda referência usa N=3, seed=0: cidades (187,226,998), (632,39,35), (618,743,783), com custos 1077, 707 e 1027 para os pares (0,1), (0,2) e (1,2).

### Validação e limites

`mvn -pl tsp-core test`: **162 testes aprovados**. `mvn clean verify`: **BUILD SUCCESS** no pai, `tsp-core` e `tsp-app`, com **166 testes**, sem falhas, erros ou testes ignorados. Ambiente: Fedora 44, Temurin 21.0.12.1 e Maven 3.9.11. Todos os 70 testes anteriores foram preservados; foram acrescentados 89 casos do gerador e sete do metadado de seed. Contagens incluem parâmetros. O [CI de push](https://github.com/Nathan1227n/Nodewalker/actions/runs/38324700957) e o [CI da PR #21](https://github.com/Nathan1227n/Nodewalker/actions/runs/38324707032) passaram em Ubuntu e Windows no commit de implementação `6729d08`; os logs da PR confirmam 166 testes por sistema e todos os módulos aprovados. `git diff --check` passou, sem arquivos `target/` versionados ou dependências gráficas no núcleo.

Os testes cobrem N mínimo/inválido; tamanhos 3, 4, 10 e 100; seeds 0, 1, -1, `Long.MIN_VALUE`, `Long.MAX_VALUE` e 657; repetições intercaladas e prefixos; extremos de coordenadas 0/1000; simetria, diagonal e separação; distância exatamente 50 e distâncias inferiores que arredondam para 50; exemplos 3-4-5 e sqrt(3); arredondamento abaixo/acima de 9,5; imutabilidade e seed. Os custos gerados são comparados também com um oráculo inteiro `BigInteger.sqrt()`, que decide o arredondamento comparando quadrados do ponto médio, sem usar `Math.sqrt` ou o cálculo do gerador.

Para N muito alto, a separação mínima em um domínio finito pode impedir novas aceitações ou causar muitas rejeições; não há garantia de término em todos os N >= 3. A matriz e sua cópia defensiva exigem memória O(N²), e os cálculos por pares são O(N²), além das tentativas rejeitadas. Uma evolução a discutir é uma API separada com orçamento explícito/cancelamento da geração e erro diagnóstico ao esgotá-lo; isso não foi imposto silenciosamente ao algoritmo contratual. `Random` usa 48 bits de estado, portanto seeds `long` diferentes podem gerar a mesma sequência; reprodutibilidade não implica unicidade entre seeds. G1 continua em andamento e nenhum solver, benchmark ou componente de UI foi implementado nesta issue.

## Contratos de rota, resultado e solver — issue #4, 12/10/2026

Entrega exclusivamente no `tsp-core`, criada da `main` com a PR #21 mesclada. Fontes: DOC 02 §§7–8, DOC 03 §§1–2, 4, 6–7, 11–12 e 19, texto da issue #4 e contratos futuros das issues #5, #8 e #9. O DOC 03 usa os nomes `Route` e `SearchResult`; esta entrega adota `TspRoute` e `TspResult`, permitidos pela issue #4. O DOC 02 descreve sete estados de solver e a issue #4 exige pelo menos os quatro terminais; o enum inclui os sete, sem implementar uma máquina de estados. Essas decisões preservam as regras normativas.

### Ciclo, custo e canonicidade

`core.model.TspRoute(List<Integer> indices)` é um record imutável que inclui o zero final: `[0, 1, 2, 0]` tem três cidades e quatro posições. `cityCount()` infere N como comprimento menos um. O construtor copia a entrada e exige N >= 3, origem 0 nas extremidades, índices interiores em [1, N), sem repetições ou ausências. A lista retornada por `indices()` é imutável e a igualdade compara a sequência armazenada. Um ciclo de três cidades é estruturalmente válido, mas incompatível com um problema de quatro cidades; essa compatibilidade é responsabilidade do validador.

`core.validation.RouteValidator.validateAndCost(TspProblem problem, TspRoute route)` confirma N e soma todas as N transições da matriz, incluindo a última aresta de retorno. Todas as acumulações usam `CostMath.addExact`; coordenadas e geometria não participam. Para custos 0–1=10, 1–2=15 e 2–0=20, `[0,1,2,0]` custa **45**, não 25. Custos zero e `Long.MAX_VALUE` são aceitos quando a soma completa é representável. Overflow, inclusive somente na última aresta, lança `ArithmeticException` com a causa original; não há saturação ou sentinela de infinito.

Ambas as orientações são ciclos válidos. `isCanonical()` verifica `route[1] < route[N-1]`; `canonical()` devolve a mesma orientação se já canônica ou inverte a sequência, mantendo zeros nas extremidades e a rota original intacta. Não ordena cidades nem escolhe entre ciclos distintos. Por exemplo, `[0,2,3,1,0]` vira `[0,1,3,2,0]`, não `[0,1,2,3,0]`. Em matriz simétrica, os custos de um ciclo e seu inverso são iguais.

A canonicidade não substitui a política de busca. Os futuros solvers deverão explorar índices em ordem lexicográfica e atualizar o incumbente apenas com custo estritamente menor. A força bruta da issue #5 deverá continuar avaliando todas as `(N-1)!` permutações, inclusive as orientações inversas, sem deduplicação. Validar um ciclo ou obter sua orientação canônica não prova ótimo global nem seleciona a primeira ótima entre ciclos distintos.

### Resultado e estados

`core.solver.SolverStatus` contém `PRONTO`, `EXECUTANDO`, `CANCELANDO`, `CONCLUIDO`, `CANCELADO`, `LIMITE_ATINGIDO` e `ERRO`. `isTerminal()` retorna verdadeiro somente para os quatro últimos. Estados e comandos de playback não pertencem a esse enum.

`TspResult` é uma classe final com construtor privado; somente estas fábricas públicas podem construir resultados:

```java
TspResult.completed(problem, route, declaredCost, metrics)
TspResult.interrupted(problem, status, optionalRoute, optionalCost, metrics, reason)
```

As fábricas recebem um `TspProblem` não nulo e validam o custo contra sua matriz. `status()`, `route()`, `cost()`, `metrics()`, `reason()` e `optimalityGuaranteed()` expõem os dados imutáveis.

| Estado | Rota e custo | Orientação | Motivo | Garantia declarada de ótimo |
| --- | --- | --- | --- | --- |
| CONCLUIDO | Obrigatórios e consistentes | Canônica obrigatória | Ausente | Sim, sob responsabilidade do solver |
| CANCELADO | Ambos presentes ou ambos ausentes | Qualquer orientação válida | Texto não vazio obrigatório | Não |
| LIMITE_ATINGIDO | Ambos presentes ou ambos ausentes | Qualquer orientação válida | Texto não vazio obrigatório | Não |
| ERRO | Ambos presentes ou ambos ausentes | Qualquer orientação válida | Texto não vazio obrigatório | Não |

Uma rota presente em execução interrompida é um **ciclo completo incumbente**, não um caminho incompleto. Seu custo sempre é recalculado e validado. O valor zero e `Long.MAX_VALUE` não significam ausência; usam-se `Optional<TspRoute>` e `OptionalLong`. Estados transitórios não podem construir `TspResult`. O motivo é uma `String` imutável, sem armazenar um `Throwable` mutável.

A fábrica `completed` rejeita orientação invertida, sem normalização automática; o chamador pode obter `route.canonical()` explicitamente. Ela valida viabilidade, orientação e custo, mas **não executa uma prova independente de ótimo**. Chamá-la declara que o solver concluiu a busca corretamente. `optimalityGuaranteed()` expressa essa declaração apenas em CONCLUIDO. Os futuros solvers devem transformar falhas de execução, inclusive overflow, em ERRO com diagnóstico, preservando somente um incumbente anterior válido, se houver. As fábricas propagam overflow ao validar um incumbente inválido, sem fabricar sucesso ou aceitar custo truncado.

Nulos falham com `NullPointerException`; violações de domínio, estado, custo ou motivo com `IllegalArgumentException`; overflow com `ArithmeticException`. Mensagens incluem posição/índice inválido, tamanho esperado ou custos declarado/recalculado, conforme o caso.

### Métricas e evolução do solver

`SolverMetrics` é um record imutável com `OptionalLong elapsedNanos`, `OptionalLong completeRoutesEvaluated`, `OptionalLong visitedNodes` e `Optional<BigInteger> discardedRoutes`. Valores presentes devem ser não negativos. `empty()` indica que nenhuma métrica foi reportada; ausência é diferente de zero medido, e cada campo pode ser reportado independentemente. Tempo usa nanossegundos; avaliações contam folhas efetivamente calculadas; nós contam estados efetivamente processados; folhas descartadas contam ciclos completos eliminados por poda, não chamadas de poda. A representação não instrumenta algoritmos nem inventa medições.

Contadores observados permanecem `long` sob limites operacionais representáveis. `discardedRoutes` aceita contagens acima de `long`; o espaço teórico permanece em `SearchSpace.completeRoutes`. A instrumentação, o histograma de podas e a igualdade `BigInteger(avaliadas) + descartadas = (N-1)!` em buscas concluídas pertencem à issue #8. Essa igualdade não é exigida para métricas ausentes ou resultados interrompidos e não é usada nesta entrega como prova de otimalidade.

`TspSolver` é uma interface funcional com `TspResult solve(TspProblem problem)`, comum aos futuros `BruteForceSolver` e `BacktrackingSolver`. A entrada é imutável, a saída deve ser não nula e terminal, e entrada nula deve falhar com `NullPointerException`. Nenhum algoritmo foi implementado. Limites, cancelamento cooperativo e listeners serão integrados na issue #9 por uma sobrecarga, preservando a entrada simples. A implementação mínima usada nos testes retorna uma solução manual conhecida e não realiza busca.

### Evidências e ponto de controle

`mvn -pl tsp-core test`: **282 testes aprovados**. `mvn clean verify`: **BUILD SUCCESS** no pai, `tsp-core` e `tsp-app`, com **286 testes**, sem falhas, erros ou testes ignorados. Os 166 testes anteriores foram preservados e foram acrescentados 120 casos: 31 de rota, 11 de validação, 20 de métricas, sete de estados, 49 de resultados e dois de interface. Contagens incluem parâmetros. Ambiente: Fedora 44, Temurin 21.0.12.1 e Maven 3.9.11. O [CI de push](https://github.com/Nathan1227n/Nodewalker/actions/runs/38327896033) e o [CI da PR #22](https://github.com/Nathan1227n/Nodewalker/actions/runs/38327899814) passaram em Ubuntu e Windows no commit de implementação `48e9c4e`; os logs da PR confirmam 286 testes por sistema e BUILD SUCCESS nos três módulos.

Não foram alterados os modelos existentes, o gerador ou a UI; não há novos frameworks ou dependências gráficas no núcleo. `git diff --check` aprovado; arquivos gerados e credenciais não pertencem à entrega. A issue #4 está implementada e aguarda revisão; G1 continua em andamento. A Etapa B de segurança do gerador não foi iniciada: sua issue, branch e implementação dependem de autorização expressa e do merge desta PR. Instrumentação, controle dos solvers e prova de ótimo continuam pendentes nas respectivas issues.

## Segurança do gerador — issue #25, 12/10/2026

A PR #22 foi mesclada e a issue #4 encerrada antes desta etapa. Após autorização expressa, a branch `feat/g1-bounded-generator` nasceu da `main` atualizada (`9bc44d6`, incluindo a PR #24). A [issue #25](https://github.com/Nathan1227n/Nodewalker/issues/25) está implementada e aguarda revisão. G1 continua em andamento; a issue #5 não foi iniciada. Os registros históricos acima descrevem as entregas anteriores.

`TspGenerator.generate(n, seed, GenerationOptions)` adiciona orçamentos explícitos positivos de candidatos e bytes estimados, token thread-safe e observação de interrupção sem limpar seu sinal. Limite, cancelamento e rejeição preliminar de memória têm exceções distintas. O preflight usa `BigInteger` para o modelo `16*N² + 256*N + 1024`, considerando duas matrizes e reservas lineares/fixas; ele não reserva heap nem garante ausência de `OutOfMemoryError`. O limite inclui candidatos rejeitados. A API original de dois argumentos permanece sem limites implícitos ou cancelamento e conserva todos os sorteios e resultados.

`TspProblem.copyOf(List<City>, long[][], long seed, Runnable checkpoint)` permite cancelar cópias defensivas e validação sem dependência do modelo para o gerador. Mantém imutabilidade e todas as regras dos construtores existentes. A sobrecarga de geração retorna somente uma instância completa; não usa resultado ou estado de solver para uma geração interrompida. Algoritmo, ordem x/y/z, separação mínima, custos e seed continuam intactos. O caso N=4, seed=657 termina com cinco tentativas; quatro falham após três cidades aceitas.

`mvn -pl tsp-core test` passou com **357 testes**; `mvn clean verify` passou nos três módulos com **361 testes**, sem falhas, erros ou ignorados. São 75 casos novos, preservando os 286 anteriores. O cancelamento entre threads foi testado com barreiras explícitas, sem sleeps. Contratos, exemplos, resultados do CI e limitações são registrados em [SEGURANCA_GERADOR.md](SEGURANCA_GERADOR.md). Limites de tempo e controle dos solvers continuam na issue #9; UI, solvers e benchmark não pertencem a esta entrega. README, ROADMAP e documentos visuais da PR #24 foram preservados.

A [PR #26](https://github.com/Nathan1227n/Nodewalker/pull/26) está aberta e referencia a issue #25, sem encerrá-la. O [CI da PR](https://github.com/Nathan1227n/Nodewalker/actions/runs/38334513100) passou em Ubuntu e Windows no commit de implementação `06c26e6`, com 361 testes por sistema e todos os módulos aprovados. `git diff --check` passou, sem arquivos gerados ou credenciais no commit.
