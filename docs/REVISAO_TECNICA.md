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

1. **Coordenadas:** o DOC 03 §3 menciona coordenadas `double` como representação geométrica; DOC 02 §12 e DOC 03 §18 especificam que o **gerador armazena inteiros** entre 0 e 1000. Na issue #2, `City` foi definido como record com coordenadas `int`, convertidas apenas para a geometria JavaFX. O modelo geral aceita todo o intervalo de `int`, inclusive coordenadas negativas; o intervalo [0, 1000], a separação mínima de 50 e a geração determinística serão responsabilidades do gerador da issue #3. Coordenadas coincidentes são válidas em uma instância manual; não se infere custo a partir delas.
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

`mvn -pl tsp-core test` passou com 66 testes e `mvn clean verify` passou nos três módulos com 70 testes: 4 de City, 47 de TspProblem, 12 de CostMath e os 7 anteriores preservados. Esses números incluem as execuções de testes parametrizados; não houve falhas, erros ou testes ignorados. Isso valida o modelo e a aritmética, sem comprovar critérios de solução ótima dos solvers ainda não implementados. A [PR #19](https://github.com/Nathan1227n/Nodewalker/pull/19) foi mesclada na `main` em 12/10/2026. O ajuste do contrato de `Collection<City>` para `List<City>` está separado na branch `fix/g1-tsp-problem-list` e aguarda integração antes da issue #3; a issue #2 continua aberta. Gerador, custos euclidianos e algoritmos não fazem parte desta implementação.

Após o ajuste para `List<City>`, os dois comandos foram executados novamente no Fedora com Java 21 e mantiveram os mesmos 66/70 testes aprovados, sem falhas, erros ou testes ignorados. O teste com `LinkedHashSet` foi adaptado para verificar a ordem de uma lista; os demais cenários de validação e imutabilidade foram preservados. `git diff --check` passou, sem arquivos `target/` versionados ou dependências JavaFX no núcleo.
