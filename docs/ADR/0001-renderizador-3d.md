# ADR 0001 — Renderizador 3D do NodeWalker

- **Status:** PROPOSTA / DECISÃO PENDENTE
- **Data:** 12/10/2026 (horário local do projeto)
- **Responsável pela decisão:** equipe do NodeWalker após prova de conceito
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
| Terreno de montanhas legível | Pendente | Pendente |
| Refúgios/ruínas/rota em cenário comum | Pendente | Pendente |
| Câmera orbital, zoom, reset | Pendente | Pendente |
| Névoa/iluminação/efeitos reduzíveis | Pendente | Pendente |
| FPS no Fedora/hardware de demonstração | Pendente | Pendente |
| Consumo de memória e início da janela | Pendente | Pendente |
| UI/HUD e threads corretas | Pendente | Pendente |
| Fallback 2D utilizável | Pendente | Pendente |
| Integração Maven 21 e CI | Pendente | Pendente |
| Tempo/esforço para concluir até 19/10 | Pendente | Pendente |
| Assets GLB, texturas e licenciamento | Pendente | Pendente |

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
4. Não converter esta proposta em “ACEITA” até registrar comparativo real e impacto nos módulos/issues.

## Decisão
**PENDENTE** — preencher após concluir a spike #23.

## Consequências e tarefas após decisão
- Atualizar issue #11 com renderizador decidido, câmera, terreno, refúgios e fallback.
- Atualizar issue #12 com threading, HUD e comunicação com `tsp-core`.
- Atualizar README e `docs/ROADMAP.md` com comandos de build/execução efetivos.
- Validar manualmente Fedora; separar evidências gráficas do CI headless; registrar Windows/fallback em #17.
- Guardar créditos/licenças de qualquer asset utilizado.
