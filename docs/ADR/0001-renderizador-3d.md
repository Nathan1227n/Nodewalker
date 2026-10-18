# ADR 0001 — Renderizador 3D do NodeWalker

- **Status:** ACEITA PARA O MVP
- **Data:** 12/10/2026; evidências da spike atualizadas em 17/10/2026 (horário local do projeto)
- **Responsável pela decisão:** responsável pelo NodeWalker, por aprovação explícita do plano da issue #11 após o merge da PR #33
- **Issue de avaliação:** [#23 — Spike jMonkeyEngine vs JavaFX](https://github.com/Nathan1227n/Nodewalker/issues/23)
- **Requisitos artísticos:** [Direção Visual](../DIRECAO_VISUAL.md)
- **Issues impactadas:** #11, #12, #13, #15 e #17
- **Prazo do MVP:** 19/10/2026

## Contexto
O núcleo do TSP está implementado em **Java 21 puro**, separado da interface JavaFX (`tsp-core` e `tsp-app`). O objetivo visual foi ampliado: mapa de refúgios em mundo pós-apocalíptico, com montanhas, ruínas, iluminação, neblina controlada e câmera orbital. O motor atual JavaFX cobre o protótipo 3D/2D; é necessário avaliar se o seu 3D atende ao impacto visual dentro do cronograma, comparado ao motor Java **jMonkeyEngine**.

A decisão não pode romper testes Maven, reprodutibilidade, fallback 2D, isolamento algorítmico ou entrega acadêmica.

## Alternativas em avaliação

### A. Permanecer em JavaFX 3D
**Benefícios esperados:** reutilização do projeto atual, menor migração, HUD e controles integrados, fallback 2D mais direto.

**Riscos a testar:** limitações em iluminação, malha/heightmap, texturização, controle de câmera e efeitos; esforço manual para aparência convincente.

### B. Usar jMonkeyEngine no mapa 3D e preservar `tsp-core`
**Benefícios esperados:** API mais especializada para terreno e câmera, materiais/iluminação e pipeline de modelos 3D; permanece no ecossistema Java.

**Riscos a testar:** configuração Java 21/Maven, drivers, loop de renderização, integração da UI, estabilidade gráfica, peso de dependências, distribuição e fallback 2D. **Não pressupor que jMonkeyEngine e JavaFX funcionarão adequadamente embutidos na mesma janela sem protótipo.**

### C. Migrar todo o produto para uma engine não Java
**Decisão provisória:** não priorizar para o MVP, pelo retrabalho do núcleo e risco de interoperabilidade/distribuição. Não impede estudos posteriores.

## Critérios obrigatórios da spike
| Critério | JavaFX 3D | jMonkeyEngine |
| --- | --- | --- |
| Terreno/refúgios/ruínas/rota | Janela real e capturas da composição comum | Janela real e capturas da composição comum |
| Câmera orbital, zoom, reset e seleção | Implementados; teste humano de conforto pendente | Implementados; teste humano de conforto pendente |
| Efeitos reduzíveis | Malha reduzida, decoração e halo opcionais | Mesmos recursos; efeitos avançados não exercitados |
| FPS apresentado no Fedora | Não medido; intervalos de callbacks registrados separadamente | Não medido; intervalos de callbacks registrados separadamente |
| Memória e início da janela | Heap/RSS e JVM até primeiro callback, conforme relatório | Mesma metodologia, incluindo memória nativa no RSS |
| UI/HUD e threads | Overlay mínimo; mesmo toolkit da aplicação | Overlay mínimo; janela/ciclo próprios, sem ponte JavaFX |
| Fallback 2D | Aplicativo atual preservado, quatro testes de seleção | Aplicativo JavaFX preservado; retorno automático de JME não implementado |
| Maven/Java 21 | Protótipo compilado separadamente | Protótipo compilado separadamente, dependências isoladas |
| Prazo #11/#12 | Estimativa provisória: 6–10 h / 8–12 h | Estimativa provisória: 10–16 h / 12–20 h |
| Assets GLB | Exige selecionar/validar importador ou conversão | Loaders opcionais documentados; importação não testada |

Registre hardware, sistema, JVM, método de medição, N/seed da cena e limitações. Evidência visual (capturas/vídeo), logs e dados obtidos devem ser vinculados na issue #23. Usar a **mesma composição de refúgios e terreno comparável** sempre que viável.

## Regras de engenharia preservadas
- `tsp-core` permanece sem JavaFX ou engine 3D.
- Custo é `TspProblem.cost(i,j)`, sem cálculo pela geometria do cenário.
- Rota corresponde a `TspRoute` e inclui retorno à origem; não confundir linha euclidiana com trilha pela superfície.
- As threads de geração, solver e renderização não podem bloquear a interface.
- `TspResult.CONCLUIDO` significa busca exata concluída; incumbente parcial não pode ser rotulada ótima.
- A alternativa JavaFX 2D atual deve ser preservada, mesmo que o motor 3D mude.
- Nenhum módulo de produção será migrado sem decisão documentada e revisão de PR.

## Regras para a decisão
1. Executar a spike **com tempo limitado** (alvo inicial de 4 horas), sem comprometer G1/G2 ou o MVP.
2. Se jMonkeyEngine trouxer ganho visual claro com risco de integração aceitável, planejar migração incremental em PR separada; manter 2D JavaFX.
3. Se a integração ou o desempenho forem arriscados, **continuar com JavaFX 3D estilizado**, reduzindo a complexidade de cena para a entrega.
4. Registrar separadamente o aceite da tecnologia e a homologação gráfica do produto. O comparativo real está preservado no relatório da spike; o aceite para o MVP foi autorizado no plano da issue #11.

## Decisão
**JavaFX 3D ACEITA PARA O MVP de 19/10/2026, com JavaFX 2D independente como fallback.** A escolha prioriza a integração com a janela e os controles existentes, o prazo, a manutenção de um único toolkit e a continuidade do fallback. O experimento básico não demonstrou ganho visual exclusivo de JME que justifique, neste prazo, integração de outra janela/ciclo e nova estratégia operacional de fallback.

A spike está em `spikes/renderer-comparison/`, fora do reactor de produção. O [relatório comparativo](../SPIKE_RENDERIZADORES.md) preserva a recomendação provisória da época, comandos, dados matemáticos, imagens reais, métricas, dependências e limitações. A issue #11 promove conceitos procedurais para `tsp-app`, sem dependência dos módulos experimentais, sem jMonkeyEngine e sem mudanças em `tsp-core` ou nos POMs.

**O aceite não afirma superioridade gráfica ou de FPS sobre jMonkeyEngine.** Não houve medição de apresentações da GPU: callbacks não certificam FPS. O teto visual das engines e os recursos avançados de JME não foram comparados. JME continua alternativa futura, mediante nova investigação e PR própria.

As [evidências da visualização real](../VISUALIZACAO_TSP.md) incluem janelas e capturas Fedora de N=3/5/8/12 e fallback por software sem Scene3D. Homologação humana de navegação/legibilidade e execução gráfica Windows permanecem explicitamente pendentes em #13/#17; o aceite da tecnologia não as substitui.

## Consequências e tarefas após decisão
- Atualizar issue #11 com renderizador decidido, câmera, terreno, refúgios e fallback.
- Atualizar issue #12 com threading, HUD e comunicação com `tsp-core`.
- Atualizar README e `docs/ROADMAP.md` com comandos de build/execução efetivos.
- Validar manualmente Fedora; separar evidências gráficas do CI headless; registrar Windows/fallback em #17.
- Guardar créditos/licenças de qualquer asset utilizado.
