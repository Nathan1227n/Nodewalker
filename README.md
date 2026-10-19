# NodeWalker

**Visualização de TSP e caminhos mínimos entre refúgios**

Aplicação desktop Java para comparar **força bruta** e **backtracking com poda**, com visualização JavaFX 3D e alternativa 2D.

**Revisão acadêmica — 19/10/2026:** o núcleo também oferece **Dijkstra clássico para caminhos mínimos**, com topologias completa, esparsa reproduzível ou manual, distâncias `BigInteger`, predecessores e execução controlada. TSP continua sendo resolvido por força bruta/backtracking; um caminho de Dijkstra não é um ciclo do caixeiro-viajante. Integração ao painel/mapa permanece para a #12. API, testes, complexidade e limites: [CAMINHOS_MINIMOS.md](docs/CAMINHOS_MINIMOS.md).

> **Estado atual — G3 em andamento:** núcleo exato auditado, geração e busca controladas; mapa JavaFX 3D/2D ligado a `TspProblem` e resultados reais, com terreno/refúgios autorais, seleção e câmera. JavaFX 3D está aceito para o MVP; JME continua somente na spike. O catálogo é limitado e reproduzível; configuração operacional/HUD completo (#12) e homologação final (#13/#17) permanecem pendentes. Incumbentes interrompidas nunca aparecem como ótimo certificado.

## Requisitos

- JDK 21 (`java -version` / `javac -version`)
- Apache Maven 3.9+ (`mvn -version`)
- Ambiente de desktop e bibliotecas gráficas somente para executar JavaFX; a CLI e os testes dispensam desktop

## Compilar e testar

Na raiz do repositório:

```bash
mvn clean verify
```

A suíte contém 2214 execuções de testes, incluindo casos parametrizados: 2022 do núcleo, 125 da CLI (120 unitários e cinco de integração do JAR) e 67 do aplicativo. Os 370 casos de caminhos mínimos preservam os 1844 anteriores. Todos são executados sem abrir janela JavaFX. `verify` inclui os testes do JAR após o empacotamento; `test` executa somente os testes unitários. A spike possui outros 20 testes, em reactor separado.

Para reproduzir uma instância no núcleo, use `TspGenerator.generate(4, 657L)`. As coordenadas, a matriz de referência e a tentativa rejeitada estão documentadas em [REVISAO_TECNICA.md](docs/REVISAO_TECNICA.md#gerador-determinístico-g1--issue-3-12102026).

`TspRoute` representa o ciclo incluindo o zero final; `RouteValidator.validateAndCost(problem, route)` valida sua compatibilidade e soma a matriz com retorno à origem e detecção de overflow. Resultados concluídos exigem orientação canônica e custo consistente; a prova de ótimo pertence ao solver, não ao validador. APIs e resultados interrompidos estão documentados em [REVISAO_TECNICA.md](docs/REVISAO_TECNICA.md#contratos-de-rota-resultado-e-solver--issue-4-12102026).

## CLI e benchmark sem JavaFX

Na raiz do repositório, estes comandos funcionam em Linux e Windows com JDK 21 e Maven, sem iniciar a aplicação gráfica:

```bash
mvn -pl tsp-cli -am clean verify
java -jar tsp-cli/target/nodewalker-cli.jar --n 4 --seed 657 --solver brute-force --warmup 3 --runs 5
```

O JAR inclui apenas a CLI e `tsp-core`, sem dependências JavaFX. Para compilação/testes completos, continue usando `mvn clean verify`. Para testes unitários da CLI e suas dependências, use `mvn -pl tsp-cli -am test`.

Trecho determinístico da saída reproduzida para esse comando. A saída também inclui `elapsedNanos` e as durações do benchmark, que variam conforme máquina, JVM e execução:

```text
Espaço teórico (N-1)!: 6
Avaliações previstas na bateria (warm-up + medições): 48
Benchmark: CONCLUIDO
Seed da instância: 657
Estado do solver: CONCLUIDO
Rota: 0 -> 2 -> 1 -> 3 -> 0
Custo: 2799
Métricas da primeira execução medida:
  completeRoutesEvaluated: 6
  visitedNodes: 6
  discardedRoutes: 0
```

`--n`, `--seed` e `--solver` são obrigatórios. Somente `brute-force` está disponível na CLI; `BacktrackingSolver` já existe no núcleo, mas sua integração ao terminal está pendente. A CLI rejeita `backtracking` e valores desconhecidos. A seed aceita todo o intervalo de `long`, inclusive negativos. Opções usam pares `--opção valor`; opções desconhecidas, repetidas ou sem valor são erros. Consulte `java -jar tsp-cli/target/nodewalker-cli.jar --help`.

| Opção | Padrão | Validação e significado |
| --- | --- | --- |
| `--n` | obrigatório | Inteiro de 32 bits, >= 3 |
| `--seed` | obrigatório | Inteiro de 64 bits |
| `--solver` | obrigatório | `brute-force` |
| `--warmup` | 3 | Inteiro >= 0; chamadas não medidas |
| `--runs` | 5 | Inteiro >= 1; chamadas medidas |
| `--max-total-routes` | 10000000 | `long` positivo; teto de avaliações da bateria inteira |
| `--max-candidates` | 100000 | `long` positivo; candidatos da geração, inclusive rejeitados |
| `--max-estimated-bytes` | 67108864 | `long` positivo; orçamento do modelo de memória da geração (64 MiB por padrão) |

O preflight confere os orçamentos de candidatos e memória antes de calcular o fatorial e gerar a instância. O modelo de memória é o já definido por `TspGenerator.estimatePeakMemoryBytes`; não reserva heap nem garante ausência de `OutOfMemoryError`. O trabalho previsto é `(warmup + runs) × (N-1)!`, calculado com `BigInteger`. Por exemplo, N=11 com os padrões exige **29.030.400** avaliações e é recusado antes da geração pelo teto de 10 milhões. Para N<=22, o espaço é exibido exatamente; acima disso, usa-se explicitamente o limite inferior `21! = 51090942171709440000`, que já excede qualquer teto `long` aceito, sem construir um fatorial gigantesco.

O teto pode ser ajustado explicitamente por `--max-total-routes`; a CLI não reduz silenciosamente a bateria nem altera o algoritmo exato. Essa proteção anterior à busca **não é um limite de tempo ou cancelamento durante `solve`**. Controles dos solvers pertencem à issue #9. A força bruta continua fatorial: não há promessa de desempenho para N grande.

A instância é gerada uma única vez com `GenerationOptions` e reutilizada, por identidade, em todos os aquecimentos e medições. O tempo de geração é separado. Cada amostra mede somente `solver.solve(problem)` entre duas chamadas a `System.nanoTime()`: não inclui geração, impressão ou conferência dos resultados. Não há listener/trilha de eventos na API atual. A saída identifica Java/SO/arquitetura, opções efetivas, durações em ordem de execução e métricas da primeira execução medida; o tempo externo do benchmark não preenche `SolverMetrics.elapsedNanos`.

A mediana ordena uma cópia das durações: para quantidade ímpar, seleciona o elemento central; para quantidade par, calcula a média aritmética exata dos dois centrais com `BigDecimal`, sem overflow de soma de `long` ou perda de meio nanossegundo. Não exige igualdade entre tempos. Exige estado CONCLUIDO, custo e rota iguais em **todas** as chamadas, inclusive aquecimentos. Qualquer erro, interrupção ou divergência encerra a bateria sem mediana, mesmo após medições válidas. A comparação definitiva entre algoritmos fica para depois do G2.

Use `--warmup 0 --runs 1` para uma execução simples. Falha ou cancelamento da geração não produz resultado de solver. Exemplo real: adicionar `--max-candidates 4` ao comando N=4/seed=657 falha após quatro candidatos e três cidades aceitas, antes de iniciar o solver. A API controlada preserva a interrupção da thread e observa seu token de cancelamento.

Códigos de saída: **0** sucesso/ajuda; **2** argumentos inválidos ou preflight recusado; **1** falha de geração, solver ou consistência; **130** cancelamento reconhecido da geração. Diagnósticos vão para stderr; saída textual usa UTF-8.

## Métricas dos solvers

Cada chamada produz um snapshot imutável em `TspResult.metrics()`. Ausência significa **não informado**; zero presente é uma medição real. Melhor custo e rota pertencem a `TspResult`.

| Métrica | Definição |
| --- | --- |
| `visitedNodes` — backtracking | Entradas processadas na recursão, incluindo raiz, estados internos, folhas e estados podados. Overflow de uma aresta antes da entrada no filho não conta esse filho. |
| `visitedNodes` — força bruta | Permutações cujo processamento começou, inclusive uma tentativa que falhe. O algoritmo iterativo não conta prefixos ou nós recursivos. Em CONCLUIDO, coincide com as avaliações. |
| `completeRoutesEvaluated` | Ciclos cujo custo completo foi calculado com sucesso, incluindo a aresta de retorno à origem. Folhas podadas e avaliações com overflow ficam fora desse contador. |
| `discardedRoutes` | Número equivalente de ciclos completos eliminados por poda, em `BigInteger`. Cada poda com `depth` cidades no caminho, incluindo a origem, elimina `(N-depth)!` folhas; na profundidade N, elimina `0! = 1`. Na força bruta, é zero. |
| `elapsedNanos` | Tempo interno de cada chamada, com `System.nanoTime()`: preparação, busca, canonicalização e consolidação/conferência das métricas, até antes da fábrica validada de `TspResult`. Exclui geração, impressão, renderização e a construção/validação final do resultado. Pode ser zero. |
| Espaço teórico | `SearchSpace.completeRoutes(N)` calcula `(N-1)!` em `BigInteger`, com origem 0 fixa e ambas as orientações. É propriedade da instância, não trabalho observado nem campo adicional do snapshot. |

O backtracking registra um histograma `long` de podas por profundidade e consolida `Σ podas[depth] × (N-depth)!` fora da recursão. Podas de subárvores distintas eliminam conjuntos disjuntos de folhas. Antes de publicar CONCLUIDO, ambos os solvers conferem exatamente:

```text
BigInteger.valueOf(completeRoutesEvaluated) + discardedRoutes
    == SearchSpace.completeRoutes(N)
```

Essa igualdade não é exigida para ERRO, CANCELADO ou LIMITE_ATINGIDO. Overflow de custo ou de contador observado e falhas de instrumentação impedem CONCLUIDO, preservando diagnóstico, métricas parciais e incumbente válido quando disponíveis. O ramo que sofreu erro não é contado como poda. Os contadores são determinísticos; o tempo pode variar entre chamadas. A mediana da CLI usa suas próprias amostras externas de `solve(problem)` inteiro, sem substituir `elapsedNanos`.

O teste N=25 usa uma matriz manual com todas as arestas zero. A primeira rota custa zero e a poda por igualdade permite concluir com **301 estados**, **1 ciclo avaliado** e **620448401733239439359999 folhas descartadas** (`24! - 1`), acima de `Long.MAX_VALUE`. Esse cenário verifica a aritmética sem enumerar 24! ciclos. Com limite de 300 nós, a busca retorna LIMITE_ATINGIDO e somente os descartes observados; com 301, conclui. O pior caso continua fatorial.

## Controle de execução dos solvers

Ambos os solvers aceitam `solve(problem, options, token)`, com cancelamento cooperativo thread-safe, observação da interrupção da thread sem limpar seu sinal e limites independentes de nós/tempo. `SolverExecutionOptions.limits(1_000_000, 2000)` permite até um milhão de estados e prazo de 2000 ms; somente a tentativa de outro estado excede o orçamento de nós. Cancelamento, limite e erro preservam incumbente completo validado e métricas parciais, sempre sem declarar ótimo comprovado.

`SolverPreflight` informa N, origem, algoritmo, limites e espaço fatorial. Para N extremo, identifica um limite inferior demonstrado quando o espaço exato não é calculado. O perfil configurável padrão permite execução explicitamente sem limites até N=10 por solver, conforme benchmark documentado; acima disso, exige pelo menos um limite. **`solve(problem)` conserva o contrato legado e não recebe limites automáticos.** Geração, CLI atual e interface gráfica mantêm ciclos de vida próprios.

Contratos, precedência, fronteiras, exemplos, benchmark reproduzível e consumo futuro pela CLI: [CONTROLE_SOLVERS.md](docs/CONTROLE_SOLVERS.md).

O conjunto de testes também audita o ótimo com Held–Karp independente em `BigInteger`, restrito a `src/test/java` e N≤12. As 1020 configurações diferenciais existentes comparam custo e rota dos dois solvers com essa referência; N=25 usa somente backtracking controlado e contagens analíticas. Recorrência, desempate, overflow, cobertura TSP-01..13 e reprodução: [AUDITORIA_OTIMO.md](docs/AUDITORIA_OTIMO.md).

## Executar o mapa real 3D/2D

O primeiro comando instala o módulo `tsp-core` no repositório local do Maven; o segundo abre a UI de `tsp-app`.

```bash
mvn clean install
mvn -f tsp-app/pom.xml javafx:run
```

O catálogo oferece N=3/5/8/12 com seeds fixas. Usa geração controlada e backtracking em background, sempre com limites explícitos de nós/tempo; N=12 pode mostrar uma incumbente sem prova de ótimo. Clique em **MAPA 2D** para alternar preservando a instância e a seleção. Sem Scene3D, inicia em Canvas 2D e desabilita 3D. Um desktop continua necessário para abrir qualquer janela.

Para iniciar explicitamente em 2D:

```bash
mvn -f tsp-app/pom.xml javafx:run -Djavafx.args="--2d"
mvn -f tsp-app/pom.xml javafx:run -Djavafx.args="--demo=12 --simplified"
```

> Em máquinas Fedora sem bibliotecas gráficas e drivers configurados, o JavaFX pode não conseguir abrir janela. Isso é um requisito do ambiente, não do algoritmo TSP.

Arraste para orbitar, use a roda para zoom e clique no refúgio/etiqueta ou escolha seu índice na lista. **R** reseta, **T** mostra o topo, **F** enquadra a seleção de perto, **M** alterna 3D/2D, **D** alterna decoração e **S** simplifica. A opção **Rótulos reduzidos / exploração limpa** conserva BASE 0 e selecionado; etiquetas secundárias sobrepostas são omitidas por prioridade determinística. Em 2D, a projeção usa XZ e a inspeção conserva Y original. As linhas são arestas diretas do TSP; terreno e câmera não definem custos.

A paisagem autoral combina vales, cristas distantes, comando central e três variantes de refúgio, com ruínas, rochas foscas e bosques de árvores, troncos mortos, arbustos e capim. Duas texturas locais de rocha/folhagem foram geradas para o projeto; não há download em execução. **D** oculta toda a ambientação; **S** reduz geometria. Rota comprovada é contínua ciano clara; incumbente é âmbar tracejada com **ÓTIMO NÃO CERTIFICADO**, sem quebra dessa indicação. Sem incumbente, não há rota desenhada. [Capturas reais e limites da validação](docs/evidencias/issue-11/vegetacao/README.md); [origem, prompts e detalhes das texturas](docs/TEXTURAS.md); [avaliação de assets e pipeline futuro](docs/ASSETS_VISUAIS.md).

Capturas de janela real e roteiro Fedora: [VISUALIZACAO_TSP.md](docs/VISUALIZACAO_TSP.md). Para repetir a coleta gráfica opcional (Python 3, desktop Linux e JDK 21):

```bash
python3 tools/review_world.py
python3 tools/review_world.py --software-auto --output tsp-app/target/review-fallback
```

O roteiro abre janelas e gera PNG, manifesto, contagens de recursos/heap/RSS pontuais e dados do ambiente; não roda no CI. Inclui fixtures de apresentação dos quatro estados, identificadas nos arquivos, além dos resultados reais do catálogo. Exercita eventos controlados, sem certificar FPS apresentado, memória estabilizada ou conforto de navegação humana. O teste nativo via Robot ficou inconclusivo no Wayland disponível.

## Estrutura

```text
NodeWalker/
├── .github/workflows/ci.yml
├── docs/                     # especificações v0.4 e notas técnicas
├── tsp-core/                 # algoritmo e modelo, sem JavaFX
├── tsp-cli/                  # CLI e benchmark, depende somente de tsp-core
├── tsp-app/                  # interface JavaFX
├── pom.xml                   # Maven multimódulo
└── README.md
```

## Roadmap e aceite

- **G0:** Maven multimódulo, Java 21, JavaFX 21.0.12, JUnit 5.14.3, hello 3D + fallback 2D. **Compilação corrigida e sete testes aprovados; demonstração original validada no Fedora. Evidências e pendências finais de CI/interface: [roadmap](docs/ROADMAP.md).**
- **G1:** modelo do grafo completo, custos, geração por seed, força bruta, testes e CLI de benchmark.
- **G2:** backtracking com poda exata, métricas, limites, cancelamento, teste diferencial.
- **G3:** mapa real 3D/2D e demonstrações limitadas na #11; HUD e fluxo operacional #12, homologação #13/#17 pendentes. JavaFX 3D aceito para o MVP após [spike #23](https://github.com/Nathan1227n/Nodewalker/issues/23), com fallback 2D obrigatório.
- **G4/G5:** camada didática e efeitos, apenas após o MVP estar estável.

Leia [docs/ROADMAP.md](docs/ROADMAP.md), [docs/REVISAO_TECNICA.md](docs/REVISAO_TECNICA.md), [docs/DIRECAO_VISUAL.md](docs/DIRECAO_VISUAL.md) e a [ADR do renderizador 3D](docs/ADR/0001-renderizador-3d.md) antes de expandir o escopo. A ADR está **ACEITA PARA O MVP** por integração, prazo, manutenção e fallback; não declara superioridade gráfica/FPS. JME não está integrado à produção.

## Fonte das especificações

Os documentos `DOC_01`, `DOC_02` e `DOC_03` (v0.4, revisados em 06/10/2026) são a fonte normativa. NodeWalker é o nome do produto; “TSP Delivery Route System” é o título de trabalho preservado nos documentos originais.
