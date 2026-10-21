# Roadmap / controle de gates

**Entrega planejada:** 19/10/2026. **Escopo do MVP oficial:** G0–G3. Não tratar G1 ou G2 isoladamente como MVP visual.

## Estado atualizado — coleta de 21/10/2026

Esta seção registra o produto após os merges reais; a tabela de 17/10 e os planos diários abaixo continuam como histórico. **19/10/2026 era a data planejada, não a data de homologação efetiva.** A coleta automatizada está concluída; aceite humano e Windows gráfico continuam pendentes. [Matriz completa](VALIDACAO_MVP.md), [evidências](evidencias/issue-13/README.md) e [roteiro acadêmico](DEMONSTRACAO_ACADEMICA.md).

| Gate | Estado efetivo | Evidência / pendência |
| --- | --- | --- |
| G0 | Base implementada e revalidada | Clone remoto, Maven vazio, verify/install/CLI/JavaFX exit0; pipeline software sem Scene3D exercitado. Desktop Windows #17 pendente |
| G1 | Modelo, geração por seed, Força Bruta e CLI implementados | Matriz preservada; mesma instância nas comparações; testes e referências independentes |
| G2 | Backtracking, execução controlada e auditoria implementados | Held–Karp, fronteiras de limites, cancelamento e extremos; sem modificação dos solvers nesta validação |
| G3 | Mapa PR #34, caminhos mínimos PR #35, painel PR #36 e pesos PR #37 mesclados | Main 9c23bb6: 2373 testes; 14 roteiros gráficos obrigatórios Fedora aprovados. #13 em revisão; mouse/teclado humano e Windows continuam PENDENTES |
| G4/G5 | Fora do escopo desta entrega | Sem Modo Aula interno, grafo dirigido, novas engines ou efeitos. Reprodução de resposta pronta não é execução passo a passo |

As issues #12/#13/#17 e a spike #23 permanecem abertas administrativamente na consulta de 21/10/2026; issue aberta não significa funcionalidade ausente. #11 está encerrada. Não se encerra issue nem se realiza merge automaticamente. Não há P0/P1 de produto demonstrado em aberto nesta coleta; incidentes do procedimento e limitações ficam no relatório, sem converter pendências externas em provas de defeito.

## Registro histórico anterior

**Revisão de escopo em 19/10/2026, após o merge da PR #34:** dois problemas separados. TSP permanece com força bruta/backtracking; caminhos mínimos passam a usar Dijkstra clássico sobre conexões completas, esparsas reproduzíveis ou manuais, com custos da matriz original. Sequência: PR A (núcleo/testes/documentação), PR B (#12: painel assíncrono e adaptação 3D/2D), PR C (#13: validação/demonstração). Isso não declara o painel concluído. A tabela e os registros históricos abaixo são preservados. Detalhes: [CAMINHOS_MINIMOS.md](CAMINHOS_MINIMOS.md).

| Gate | Conteúdo | Critério de saída | Situação em 17/10/2026 (incluindo esta PR) |
| --- | --- | --- | --- |
| G0 | Maven multimódulo, Java 21, JavaFX 21.0.12, JUnit 5.14.3, hello 3D/fallback | Compilação, testes e demonstração no Fedora; verificações gráficas complementares no Windows/sem Scene3D | Concluído — issue #1 encerrada e PR #16 mesclada; verificações complementares na issue #17 |
| G1 | Modelo, gerador, contratos, custos, força bruta e CLI de benchmark | Correção e determinismo, testes sem UI | Implementado, incluindo geração controlada e CLI sem JavaFX |
| G2 | Backtracking, poda, métricas, limites e cancelamento | Resultados iguais aos da força bruta e invariantes preservadas | Núcleo implementado e auditado; PR #31 de controles e PR #32 de Held–Karp mescladas; baseline anterior 1781 testes |
| G3 | Mapa 3D pós-apocalíptico, 2D seguro, câmera, HUD e execução assíncrona | UI responsiva, visual marcante e rota auditável | Em andamento — spike PR #33 mesclada; JavaFX 3D aceito; mapa real #11 nesta PR; #12/#13 pendentes |
| G4 | Modo Aula e recursos didáticos | Testes do MVP continuam passando | Posterior |
| G5 | Efeitos opcionais | Não interferem no TSP | Opcional |

## Plano diário (documento de consolidação de 11/10/2026)

- **11/10:** estabelecer G0 e abrir janela JavaFX 3D/fallback 2D.
- **12/10:** TspProblem, City, gerador, matriz de custos, validação e brute force.
- **13/10:** backtracking com poda e métricas.
- **14/10:** testes diferenciais, invariantes, cancelamento/limites, benchmark.
- **15/10:** mapa 3D, câmera e rota final destacada.
- **16/10:** interface assíncrona, HUD, reexecução, preflight.
- **17/10:** identidade visual; apenas melhorias seguras.
- **18/10:** revisão, documentação e teste em clone limpo.
- **19/10:** demonstração/entrega; sem novas funcionalidades de risco.

## G0: concluído — evidências e verificações complementares

O G0 foi concluído: a [issue #1](https://github.com/Nathan1227n/Nodewalker/issues/1) foi encerrada e a [PR #16](https://github.com/Nathan1227n/Nodewalker/pull/16) foi mesclada na `main` em 12/10/2026 (America/Fortaleza). Compilação Maven, sete testes JUnit, CI Ubuntu/Windows e execução gráfica no Fedora foram comprovados. As validações gráficas em Windows real e em ambiente sem Scene3D permanecem abertas na [issue #17](https://github.com/Nathan1227n/Nodewalker/issues/17), como verificações complementares que não bloqueiam o início do G1.

As evidências anteriores abaixo foram informadas pelo desenvolvedor; as verificações da correção foram executadas em 11/10/2026 (America/Fortaleza). A confirmação visual posterior no Fedora foi fornecida pelo desenvolvedor e está registrada no [comentário de encerramento da issue #1](https://github.com/Nathan1227n/Nodewalker/issues/1#issuecomment-6117014785) e no checklist abaixo.

### Já validado pelo desenvolvedor antes desta correção

- [x] Java 21 e Maven disponíveis no Fedora.
- [x] Compilação original com `mvn clean verify` e 3 testes JUnit aprovados em `SearchSpaceTest`.
- [x] Janela JavaFX iniciando no Fedora, com visualização 3D no hardware disponível.
- [x] Botão “Alternativa 2D” exibindo o mapa bidimensional.
- [x] Código inicial publicado na `main` do GitHub.
- [x] Workflow anterior aprovado em Ubuntu e Windows.

### Verificado nesta correção

- [x] Alterações locais preservadas na branch `fix/g0-fallback-validation`, incluindo Actions v5, dependência JUnit, seletor e quatro testes.
- [x] Erro de compilação reproduzido e corrigido: faltava `{` após `private void setPreview(Node content, String mode)`. Os erros sobre classes sem nome/preview eram consequências da sintaxe; não foi habilitado `--enable-preview`.
- [x] Seleção inicial isolada em `PreviewModeSelector`, sem dependência de JavaFX: `supports3d && !force2d`.
- [x] Quatro combinações testadas com JUnit: suporte sem `--2d` → 3D; suporte com `--2d` → 2D; ausência de suporte com ou sem `--2d` → 2D.
- [x] Integração revisada no código: detecção por `Platform.isSupported(ConditionalFeature.SCENE3D)`, argumento `--2d`, textos de modalidade, alternância manual e botão 3D desabilitado sem suporte preservados. `DemoPreview` e os três testes de `SearchSpaceTest` não foram alterados; nenhum solver foi implementado.
- [x] `mvn clean verify`: pai, `tsp-core` e `tsp-app` com `SUCCESS`; 7 testes, 0 falhas, 0 erros e 0 ignorados.
- [x] `mvn clean install`: os três módulos com `SUCCESS`, os mesmos 7 testes aprovados e artefatos instalados no repositório Maven local.
- [x] `env -u DISPLAY -u WAYLAND_DISPLAY mvn --batch-mode --no-transfer-progress clean verify`: `BUILD SUCCESS`, com os mesmos 7 testes, demonstrando execução sem conexão com o desktop.
- [x] Workflow revisado: `actions/checkout@v5`, `actions/setup-java@v5`, Temurin 21, `cache: maven`, matriz Ubuntu/Windows e `mvn --batch-mode --no-transfer-progress clean verify` preservados.

Ambiente desta execução: Fedora Linux, Eclipse Temurin **21.0.12.1** e Apache Maven **3.9.11**. Os relatórios ficam em `tsp-core/target/surefire-reports/` (3 testes) e `tsp-app/target/surefire-reports/` (4 testes), gerados localmente e excluídos do Git.

Durante a correção, `mvn -f tsp-app/pom.xml javafx:run` e `mvn -f tsp-app/pom.xml javafx:run -Djavafx.args=--2d` foram iniciados após a instalação e permaneceram em execução sem erro inicial no console. Ambos foram encerrados com Ctrl+C (código 130), não por falha observada de JavaFX. Essa observação de processo, isoladamente, não comprova a aparência da janela nem a interação com os botões. Posteriormente, o desenvolvedor confirmou visualmente no Fedora a inicialização 3D, a alternância manual 2D/3D e a inicialização forçada com `--2d`.

O primeiro [CI de push](https://github.com/Nathan1227n/Nodewalker/actions/runs/38196497489), no commit `8f82fea`, passou em Ubuntu e Windows com 7 testes por sistema. A [execução separada da PR](https://github.com/Nathan1227n/Nodewalker/actions/runs/38196509084) passou no Windows, mas falhou no Ubuntu ao resolver `maven-clean-plugin:3.5.0` no Maven Central, antes de compilar. Os logs mostraram versões diferentes dos plugins implícitos entre os ambientes. Por isso, o POM pai agora fixa os plugins do ciclo de build nas versões já validadas localmente: clean 3.2.0, resources 3.3.1, jar 3.4.1 e install 3.1.2; compiler e surefire permanecem nas versões existentes. Após o ajuste (`426edd4`), `mvn clean verify` e `mvn clean install` passaram novamente no Fedora. O [CI de push](https://github.com/Nathan1227n/Nodewalker/actions/runs/38196725604) e o [CI da PR](https://github.com/Nathan1227n/Nodewalker/actions/runs/38196728956) passaram em Ubuntu e Windows; os logs da PR confirmam as versões fixadas e 7 testes por sistema, sem falhas, erros ou testes ignorados.

As Actions v5 usam Node.js 24; a documentação de [checkout](https://github.com/actions/checkout/tree/v5) e [setup-java](https://github.com/actions/setup-java/tree/v5) foi consultada. `setup-java@v5` requer runner 2.327.1 ou posterior e o cache Maven considera os POMs do repositório por padrão. O workflow usa runners hospedados `ubuntu-latest` e `windows-latest`; a compatibilidade foi confirmada nas execuções aprovadas acima (runner 2.337.0).

### Encerramento do G0

- [x] Nova execução do workflow com Actions v5 aprovada em Ubuntu e Windows, com links registrados acima.
- [x] [PR #16](https://github.com/Nathan1227n/Nodewalker/pull/16) mesclada na `main`.
- [x] Desenvolvedor confirmou visualmente esta revisão no Fedora: início 3D quando disponível, troca para 2D, retorno ao 3D e início com `--2d`.
- [x] [Issue #1](https://github.com/Nathan1227n/Nodewalker/issues/1) encerrada; verificações gráficas restantes registradas na issue #17.

### Verificações complementares pendentes — issue #17

- [ ] Validar renderização e legibilidade do fallback automático em ambiente real sem Scene3D, incluindo botão 3D desabilitado.
- [ ] Validar abertura e uso da janela JavaFX no Windows real, incluindo 3D quando suportado e alternativa 2D.
- [ ] Registrar resultados, capturas de tela e limitações dessas verificações na documentação.

Esses itens são acompanhados pela [issue #17](https://github.com/Nathan1227n/Nodewalker/issues/17), que permanece aberta. Não foram marcados como realizados nem impedem o início do G1.

**Limites das evidências:** os testes unitários comprovam a decisão de seleção, mas não a renderização sem Scene3D. Não foi adicionado teste de integração gráfico à suíte headless. O CI no Windows comprova compilação e testes, não a abertura real da janela. A validação visual confirmada refere-se ao Fedora; a conclusão do G0 mantém explícitas as verificações complementares da issue #17. Nenhum solver de G1/G2 foi implementado nesta correção.

## G1: modelo de domínio — issue #2

Implementação da [issue #2](https://github.com/Nathan1227n/Nodewalker/issues/2) na branch `feat/g1-city-tsp-problem`, com a [PR #19](https://github.com/Nathan1227n/Nodewalker/pull/19) mesclada na `main` em 12/10/2026. O ajuste posterior de `Collection<City>` para `List<City>` foi integrado pela [PR #20](https://github.com/Nathan1227n/Nodewalker/pull/20), com [CI aprovado em Ubuntu e Windows](https://github.com/Nathan1227n/Nodewalker/actions/runs/38323819899). A issue #2 foi encerrada após confirmação do merge; o G1 completo continua em andamento.

- [x] `City` imutável com índice não negativo e coordenadas inteiras; restrições de faixa e separação reservadas ao futuro gerador.
- [x] `TspProblem` com N >= 3, lista de cidades ordenada por índice e origem fixa 0; validação integral de entradas nulas e da matriz manual quadrada, simétrica, não negativa e com diagonal zero.
- [x] Cópias defensivas da lista e das linhas da matriz; lista retornada imutável, matriz interna não exposta e índices de acesso verificados.
- [x] `CostMath.addExact` detecta overflow com exceção explícita; nenhum custo é truncado ou saturado. Decisões registradas em [REVISAO_TECNICA.md](REVISAO_TECNICA.md).
- [x] `mvn -pl tsp-core test`: `BUILD SUCCESS`, 66 testes, sem falhas, erros ou testes ignorados.
- [x] `mvn clean verify`: pai, `tsp-core` e `tsp-app` com `SUCCESS`; 70 testes no total, sem falhas, erros ou testes ignorados.
- [x] Sete testes anteriores preservados; 63 novos casos executados (4 de City, 47 de TspProblem e 12 de CostMath, incluindo parâmetros), independentes de JavaFX.
- [x] PR #19 revisada e mesclada; CI aprovado em Ubuntu e Windows.
- [x] Ajuste para `List<City>` integrado na main pela PR #20 antes de criar a branch da issue #3.
- [x] Issue #2 encerrada após confirmação do merge.

Gerador aleatório, matriz euclidiana, força bruta e backtracking não foram implementados nesta issue. Os testes de domínio não comprovam os critérios de solução ótima e determinismo dos futuros solvers.

## G1: gerador determinístico e custos 3D — issue #3

Implementado na branch `feat/g1-deterministic-generator`, criada da `main` atualizada no commit de merge `51f036f` da PR #20. A [PR #21](https://github.com/Nathan1227n/Nodewalker/pull/21) foi mesclada em 12/10/2026 (America/Fortaleza), no commit `02eae42`; a issue #3 foi encerrada após validação do gerador e revisão dos testes. G1 não foi concluído.

- [x] Documentos DOC 02 §12 e DOC 03 §§3 e 18 conferidos; API `TspGenerator.generate(int n, long seed)` no núcleo.
- [x] N >= 3, um `Random(seed)` por geração, sorteios x/y/z nessa ordem e consumo integral das tentativas rejeitadas.
- [x] Coordenadas inteiras em [0, 1000], índices sequenciais e separação euclidiana 3D >= 50, sem arredondar na rejeição.
- [x] Matriz `long` com diagonal zero, custos por `Math.round(Math.sqrt(...))` e cálculo único por par não ordenado.
- [x] Seed preservada como metadado imutável opcional em `TspProblem`, sem retirar o construtor de matriz manual.
- [x] Referências explícitas para N=3/seed=0 e N=4/seed=657, incluindo um candidato rejeitado e a sequência posterior correta.
- [x] `mvn -pl tsp-core test`: 162 testes aprovados; `mvn clean verify`: todos os módulos aprovados, 166 testes no total, sem falhas, erros ou testes ignorados.
- [x] Todos os 70 testes anteriores preservados; 89 novos casos do gerador e sete do metadado de seed, sem inicialização de JavaFX.
- [x] `git diff --check` aprovado; sem arquivos `target/` versionados ou dependências gráficas no núcleo.
- [x] [CI de push](https://github.com/Nathan1227n/Nodewalker/actions/runs/38324700957) e [CI da PR #21](https://github.com/Nathan1227n/Nodewalker/actions/runs/38324707032) aprovados em Ubuntu e Windows no commit `6729d08`; logs da PR confirmam 166 testes por sistema e todos os módulos aprovados.
- [x] PR #21 revisada e mesclada antes de iniciar os contratos da issue #4.
- [x] Issue #3 encerrada após o merge da PR #21; critérios de aceite marcados e evidências registradas.

O exemplo reproduzível, a matriz completa, o critério de rejeição e os limites de memória/término estão em [REVISAO_TECNICA.md](REVISAO_TECNICA.md#gerador-determinístico-g1--issue-3-12102026). Força bruta, backtracking, benchmark e interface permanecem para as próximas tarefas.

## G1: contratos de rota, resultado, solver e validação — issue #4

Implementado na branch `feat/g1-route-solver-contracts`, criada da `main` atualizada no merge `02eae42` da PR #21. A [PR #22](https://github.com/Nathan1227n/Nodewalker/pull/22) foi mesclada na `main` em 12/10/2026 (horário de Fortaleza), commit `83996e3`; a issue #4 foi encerrada após validação. G1 não foi concluído.

- [x] `TspRoute` imutável com cópia defensiva, ciclo incluindo zero final, índices válidos e cobertura integral.
- [x] `RouteValidator.validateAndCost` verifica compatibilidade e soma todas as arestas da matriz, inclusive retorno, com `CostMath.addExact`.
- [x] Validade aceita ambas as orientações; canonicidade é explícita e resultados concluídos exigem `route[1] < route[N-1]`.
- [x] `SolverStatus` separa estados do solver do playback; `TspResult` admite somente resultados terminais com incumbente/custo consistentes, motivo e garantia de ótimo declarada somente em CONCLUIDO.
- [x] `SolverMetrics` distingue valor ausente de zero medido, com contadores observados `long` e folhas descartadas `BigInteger`; instrumentação fica para a issue #8.
- [x] Interface `TspSolver.solve(TspProblem)` compartilhável pelos futuros algoritmos; controle/listeners ficam para a issue #9.
- [x] `mvn -pl tsp-core test`: 282 testes aprovados; `mvn clean verify`: três módulos aprovados, 286 testes totais, sem falhas, erros ou testes ignorados.
- [x] Todos os 166 testes anteriores preservados; 120 novos casos sem inicialização de JavaFX. UI, gerador e modelos existentes preservados.
- [x] `git diff --check` aprovado; sem arquivos gerados ou credenciais no commit e sem dependências gráficas adicionadas ao núcleo.
- [x] [CI de push](https://github.com/Nathan1227n/Nodewalker/actions/runs/38327896033) e [CI da PR #22](https://github.com/Nathan1227n/Nodewalker/actions/runs/38327899814) aprovados em Ubuntu e Windows no commit `48e9c4e`, com 286 testes por sistema e todos os módulos aprovados.
- [x] PR #22 revisada e mesclada; issue #4 encerrada com critérios de aceite completos.

Contratos, responsabilidades e limitações estão em [REVISAO_TECNICA.md](REVISAO_TECNICA.md#contratos-de-rota-resultado-e-solver--issue-4-12102026). Validar um ciclo e seu custo não prova ótimo global. Nenhum algoritmo, benchmark, controle completo de execução ou componente de UI foi implementado nesta entrega.

A Etapa B de segurança do gerador foi **autorizada após o merge da PR #22** e está sendo trabalhada separadamente pelo Codex; ainda não há merge de sua implementação confirmado neste documento. Seus limites são distintos do controle de execução dos solvers da issue #9. Esta revisão documental não altera nem interfere na branch da Etapa B.


## G3: direção visual pós-apocalíptica — planejamento atualizado

**Visão:** explorar rotas ótimas entre os últimos refúgios seguros de um mundo montanhoso em colapso, com identidade própria inspirada na atmosfera de travessia e isolamento da ficção científica. Não é um jogo nem altera a matemática do TSP.

**Documentos oficiais:** [DIRECAO_VISUAL.md](DIRECAO_VISUAL.md) e [ADR 0001 — Renderizador 3D](ADR/0001-renderizador-3d.md), **ACEITA PARA O MVP** por aprovação explícita após a PR #33. O detalhamento implementado está em [VISUALIZACAO_TSP.md](VISUALIZACAO_TSP.md).

**Decisão de tecnologia:** JavaFX 3D estilizado, com 2D independente. Integração existente, prazo, manutenção e fallback justificam o aceite; o comparativo não demonstrou superioridade gráfica ou de FPS de uma engine. JME permanece isolado e pode ser reavaliado após o MVP.

### Spike #23 — protótipos isolados (17/10/2026)

Dois protótipos Java 21/Maven em `spikes/renderer-comparison/` compartilham cenário procedural, cinco refúgios, câmera e ciclo fixo validado. A PR #33 foi mesclada em `29fc04c`. Janelas e capturas NVIDIA/Fedora, medições e a recomendação então provisória estão preservadas em [SPIKE_RENDERIZADORES.md](SPIKE_RENDERIZADORES.md). A aceitação posterior de JavaFX para o MVP não altera esses resultados históricos. Compilação em CI não equivale a homologação visual.

### Mapa real — issue #11 (esta PR)

- Modelo visual imutável derivado de `TspProblem`/`TspResult`, transformação XYZ reversível e ciclo exato, inclusive retorno. Custos continuam na matriz original.
- Terreno autoral preparado em background, vales e cristas distantes, terminal BASE 0 âmbar, três variantes de abrigos ciano, ruínas periféricas e materiais dessaturados. Rota certificada contínua ou incumbente tracejada, seleção/XYZ, órbita, zoom, reset, topo e foco.
- Canvas 2D independente, alternância preservando modelo/seleção/câmera, complexidade reduzível e até 32 conexões opcionais do selecionado.
- Catálogo N=3/5/8/12 com seeds fixas, geração e backtracking limitados, tokens distintos e descarte de entregas obsoletas. Não é o controlador operacional da #12.
- Textos de certificação separados e sem quebra da negação; prioridade/colisão determinística de rótulos e modo de exploração limpa, sem mover posições lógicas.
- 63 novos testes headless na PR (38 iniciais + 25 do refinamento; **1844 no produto**, 67 no app), [janelas/capturas Fedora reais e contagens de recursos](evidencias/issue-11/vegetacao/README.md), fallback por Prism software. A correção de superfície adiciona albedo rochoso fosco, UVs para encostas e normais coerentes entre resoluções, sem alterar dados TSP. Navegação humana, desempenho sustentado e Windows gráfico ainda exigem homologação #13/#17.
- [Candidatos a assets, licenças específicas e limites de importação](ASSETS_VISUAIS.md) registrados; geometria do MVP continua autoral, sem importador ou dependência nova.

As seções anteriores são registros históricos de cada entrega e mantêm suas contagens e limitações da época.

### Escopo visual obrigatório para o MVP (G3)

- **Issue #11:** terreno montanhoso estilizado e leve, vales/platôs, alguns props abandonados, refúgios luminosos nos índices reais, origem 0 distinta, câmera orbital/zoom/reset e rota ótima claramente desenhada e auditável.
- **Issue #12:** HUD escuro com estados e métricas reais; preflight, geração segura (Etapa B) e busca em threads apropriadas; cancelamento da geração separado do cancelamento dos solvers.
- **Issue #13:** validar cena no Fedora, fallback 2D, relação exata entre `TspProblem`, rota e visualização, build limpo, licenças dos assets e desempenho adequado para a demonstração.
- **Issue #17:** manter verificação gráfica real no Windows e de fallback automático sem Scene3D (CI headless não comprova visual).

**Regra matemática:** o custo continua sendo a distância **euclidiana 3D direta** de `TspProblem`, não o comprimento de um caminho que sobe/desce montanhas. Ajustes de cenário são somente visuais; não mover as cidades lógicas nem alterar a matriz. Em resultados interrompidos, exibir incumbente como **não ótima comprovada**.

### Polimento opcional (G5)

A [issue #15](https://github.com/Nathan1227n/Nodewalker/issues/15) fica reservada a flyover cinematográfico, pulsos, chuva, partículas, névoa avançada e animações sofisticadas. Reduzir ou dispensar esses efeitos se ameaçarem responsividade, compatibilidade, prazo ou correção dos algoritmos. Não usar assets copiados de jogos: preferir modelos próprios em Blender e recursos com licença confirmada (por exemplo, CC0).

### Sequência recomendada e corte de riscos

1. Preservar o núcleo, a geração controlada e as auditorias já integradas pelas issues #5–#10.
2. Revisar esta PR da #11 com a ADR aceita, capturas e reprodução do mapa real.
3. Implementar #12 em outra branch: configuração, HUD completo e coordenação operacional assíncrona; reservar tempo para testes manuais.
4. Fechar #13 e entregar até 19/10/2026; se necessário, reduzir detalhes do terreno/ruínas/efeitos, **nunca** correção do TSP, legibilidade, cancelamento ou fallback.

**Controle de mudanças:** a presente direção artística é evolução de apresentação, não alteração das especificações normativas DOC 01–03. Custos por caminhos topográficos ou jogabilidade exigiriam especificação futura própria.


## Integração operacional — issue #12 — 19/10/2026

O fluxo operacional substitui a execução automática do catálogo por geração e busca explícitas.
Força bruta/backtracking permanecem TSP; Dijkstra clássico mantém contratos próprios de caminhos mínimos.
As seções anteriores ficam preservadas como histórico. Arquitetura, uso, limites e validação desta entrega:
[PAINEL_OPERACIONAL.md](PAINEL_OPERACIONAL.md). A homologação completa da #13 continua separada.
