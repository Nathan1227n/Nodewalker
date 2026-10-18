# Mapa real de refúgios — issue #11

Implementação em `tsp-app`, sobre o merge `29fc04c` da PR #33. JavaFX 3D está **ACEITA PARA O MVP** na [ADR 0001](ADR/0001-renderizador-3d.md), por integração, prazo, manutenção e fallback. A decisão não afirma superioridade gráfica ou de FPS sobre jMonkeyEngine. O [relatório histórico da spike](SPIKE_RENDERIZADORES.md) e seus módulos permanecem preservados.

## Arquitetura e entrada de apresentação

| Camada | Contrato implementado |
| --- | --- |
| Fonte de verdade | `TspProblem`, `City`, `TspResult`, `TspRoute` do núcleo, sem alterações |
| Modelo | `world.TspSceneModel.of(problem, Optional<TspResult>)`: dados e coleções imutáveis, exatamente N refúgios, rota recebida, metadados e terreno preparado |
| Matemática visual | `WorldTransform`, `MapProjection`, `SegmentGeometry`, `OrbitCamera`, `Vec3`: Java puro, sem toolkit ou janela |
| Geometria | `WorldGeometry`, `TerrainFactory`, `RefugeGeometryFactory`, `PropGeometryFactory`, `VegetationFactory`: paisagem, quatro silhuetas, props e vegetação imutáveis preparados no worker, agrupados por material |
| Apresentação | `ResultPresentation`: interpretação única de estado, incumbente e certificação; `LabelLayout`: prioridade/colisão em tela, sem mover cidades |
| Navegação | `WorldViewState`: seleção, câmera e preferências independentes do renderer |
| Entrada FX | `world.render.TspWorldView.setModel(model)` e `modelProperty()` somente na FX Application Thread |
| Adaptadores | `World3DRenderer` / `Map2DRenderer`, ambos com descarte explícito |
| Demonstração | `DemoCatalog` / `DemoLoader`: quatro presets, executor único, tokens distintos e entrega protegida por identificador |

Exemplo de integração futura, sem solver dentro do visualizador:

```java
// Worker: objetos puros, validação pela matriz e preparação de terreno.
TspSceneModel prepared = TspSceneModel.of(problem, Optional.of(result));
// Entregar somente se ainda corresponder à execução ativa.
Platform.runLater(() -> worldView.setModel(prepared));
```

`withResult(Optional<TspResult>)` compartilha toda a geometria preparada da mesma instância. Atualizações de resultado da mesma referência de `TspProblem` preservam a câmera/seleção, o terreno e a SubScene; substituem somente as conexões da rota/seleção. O controlador da #12 deve vincular resultados à instância/executação correta. `TspResult` não armazena identidade de problema: o modelo confere tamanho, ciclo e custo contra a matriz fornecida, mas não pode detectar duas instâncias diferentes com ciclos e custos coincidentes.

Não há novo módulo, dependência experimental, JME, asset externo, download em runtime ou alteração em `tsp-core`, CLI, POMs e CI.

## Fidelidade matemática

Seja `c` o centro da caixa XYZ lógica e `s = 1000 / maiorExtensão`. Para extensão nula, `s=1`. A transformação é:

```text
visual = (s(x−cx), −s(y−cy), −s(z−cz))
lógico = (cx+vx/s, cy−vy/s, cz−vz/s)
```

Y lógico é altitude; JavaFX usa Y positivo para baixo. A inversão conjunta de Y/Z preserva orientação. O cálculo converte coordenadas para `double` antes de subtrações, inclusive nos extremos de `int`. A escala é uniforme; não deforma relações XYZ. O objeto `WorldTransform` expõe centro, escala e inversa.

Cada marcador de conexão ocupa exatamente a coordenada transformada da cidade. Cabines, plataformas, fundações e o relevo são decorativos e podem se adaptar à posição. Nenhuma cidade lógica é movida para uma montanha. A rota usa a lista original, sem ordenar/canonicalizar novamente, formando exatamente N segmentos consecutivos, inclusive o retorno 0. Custos por segmento vêm de `problem.cost(from,to)`; o custo total é conferido por `RouteValidator`, incluindo retorno. Não se mede comprimento visual para publicar custo. Matrizes manuais podem ter custos sem relação com a geometria.

As arestas representam segmentos diretos no espaço, não estradas sobre o terreno. Podem passar por uma montanha ou se ocultar por perspectiva; topo, órbita, inspeção e mapa XZ ajudam a auditar. Coordenadas coincidentes não são deslocadas artificialmente. Um segmento de comprimento visual zero mantém seus índices/custo; usa indicação pontual em 3D e um pequeno anel de indicação em 2D, sem inventar um desvio do TSP.

## Estados e valores ausentes

| Entrada | Apresentação |
| --- | --- |
| Sem `TspResult` | SEM RESULTADO, nenhuma rota |
| `status()==CONCLUIDO && optimalityGuaranteed()==true` | ROTA ÓTIMA; ÓTIMO CERTIFICADO, rota contínua ciano clara |
| CANCELADO / LIMITE_ATINGIDO / ERRO com incumbente | Estado original e motivo; MELHOR ROTA CONHECIDA — ÓTIMO NÃO CERTIFICADO, rota âmbar tracejada |
| Interrupção/erro sem incumbente | Sem rota desenhada; nenhum ciclo fictício |
| Custo/métrica ausente | “não informado”, sem substituir por zero |
| Custo `Long.MAX_VALUE` | Valor legítimo, formatado como inteiro; nunca sentinela |

A certificação replica a declaração contratual de `TspResult`; o visualizador não realiza uma nova prova de ótimo. Métricas parciais permanecem as observadas; não exige cobertura fatorial nem fabrica descartes. Erro de geração/carregamento é apresentado separadamente, sem fabricar `TspResult.ERRO`, CANCELADO ou LIMITE_ATINGIDO.

**Diagnóstico do HUD anterior:** a frase negativa “MELHOR ROTA CONHECIDA · NÃO ÓTIMO CERTIFICADO” podia quebrar deixando “CERTIFICADO” sozinho na linha seguinte. O resultado N=12 era `LIMITE_ATINGIDO` e a lógica de certificação já respeitava o contrato; não foi identificado solver declarando ótimo nessa interrupção. O refinamento separa título e certificação, mantém **“ÓTIMO NÃO CERTIFICADO” sem quebra** e testa os quatro estados e suas transições. Nenhuma regra do solver foi alterada.

## Cena, navegação e fallback

O terreno combina região central, vales, áreas abertas, altos platôs e cristas distantes. Um anel de malha mais grossa continua a geografia até **3,6× a extensão central**, sem a antiga saia vertical que parecia delimitar uma ilha. A fronteira dos dois grids é exatamente coincidente, inclusive nas normais derivadas da mesma superfície. Após a correção solicitada pela captura, o gradiente foi substituído por albedo de basalto/ardósia, com UVs por plano dominante para não esticar paredes, material fosco sem especular e iluminação fria mais escura. [Diagnóstico, origem do asset, prompt e limites](TEXTURAS.md). Há gradiente de fundo, luz ambiente e duas direcionais; **não é névoa física, bloom, shadow mapping ou PBR**.

BASE 0 tem plataforma em dois níveis, comando modular chanfrado, ala de serviço, torre/radar, painéis, gerador, escada e luz âmbar. Os secundários alternam deterministicamente **comunicação**, **manutenção/suprimentos** e **monitoramento**, com beacons ciano. São exatamente N conjuntos: nenhum prop recebe índice ou marcador de cidade. Fundações podem ficar altas quando duas cidades próximas em XZ têm altitudes muito diferentes; os pontos de conexão nunca são movidos para disfarçar isso.

Props incluem torre danificada, ruína industrial, depósito/gerador e grupos de rochas, em posições determinísticas periféricas, com exclusão de 120 unidades visuais em torno dos pontos XZ. Ocultar decoração só muda visibilidade, sem regenerar terreno. A variante simplificada remove detalhes e reduz terreno/props; ambas são preparadas uma vez por instância. Não há rebuild por frame nem assets externos. [Pesquisa de assets e avaliação de importação](ASSETS_VISUAIS.md).

A vegetação adicional forma oito regiões: árvores de copas irregulares texturizadas, galhos mortos, arbustos, capim e cobertura vegetal. No máximo 384 candidatos são filtrados por inclinação e distância aos refúgios; rochas e plantas apoiam-se na malha detalhada realmente desenhada. A qualidade simplificada usa um subconjunto dos mesmos IDs/XZ e menos componentes. **D / Vegetação, rochas e ruínas** controla todo esse conjunto; nenhuma planta é um nó TSP. [Texturas, prompts e contratos de vegetação](TEXTURAS.md).

A rota comprovada é contínua; a incumbente usa nove traços por aresta, com lacunas reais, sem halo contínuo fechando os intervalos. A espessura é ajustada em eventos de câmera, dentro de limites, sem mover extremidades. Trechos podem ficar ocultos pelo relevo; topo/2D permitem conferir o mesmo ciclo. Nenhuma conexão é deslocada para acompanhar a superfície.

As etiquetas têm prioridade explícita: selecionado, BASE 0 e depois índice crescente. `LabelLayout` tenta posições limitadas, evita colisões, mantém etiquetas no viewport e omite secundárias sem espaço. BASE/selecionado têm alternativas nas bordas com linha de chamada; os marcadores não se movem. Rótulos secundários compactos e o modo **Rótulos reduzidos / exploração limpa** ajudam em N=8/12. A lista lateral mantém todos os índices acessíveis. O algoritmo é o mesmo no Canvas 2D e no overlay 3D.

| Ação | Controle |
| --- | --- |
| Órbita horizontal/vertical | Arrastar botão primário pelo viewport, incluindo céu vazio |
| Zoom | Roda, com limites |
| Reset e enquadramento da instância | R ou botão RESET visível no mapa |
| Topo verdadeiro | T ou SUPERIOR, base ortonormal definida mesmo no polo |
| Seleção | Clique no abrigo/etiqueta; lista de todos os índices para pontos ocultos/coincidentes |
| Foco do selecionado | F, botão ou duplo clique no abrigo |
| Alternar modo | M ou botões 3D / MAPA 2D |
| Decoração / simplificação | D / S ou caixas de seleção |
| Rótulos reduzidos | Caixa de seleção em EXPLORAÇÃO: BASE 0 e selecionado |
| Arestas secundárias | Caixa de seleção: até 32 ligações do selecionado, omitindo as da rota |

Reset/topo consideram os limites reais da instância e o aspecto do viewport, não o anel distante. A paisagem ampliada aumenta o far clip sem tornar os refúgios minúsculos no enquadramento. Near/far são proporcionais à cena. Foco permite inspecionar a arquitetura; zoom/foco podem tirar outros pontos do campo visual de propósito; reset recupera o conjunto. Mudanças de tamanho atualizam a câmera sem depender de cinco posições fixas.

O Canvas 2D não cria SubScene, materiais ou formas 3D. Projeta XZ ortograficamente, com +X à direita e +Z para cima; Y original aparece nas etiquetas/inspeção. Projeções coincidentes mantêm a posição correta e podem ser percorridas por clique ou pela lista. A escala trata extensões nulas. `--2d` força início em 2D; sem `ConditionalFeature.SCENE3D` o fallback é automático e o botão 3D fica desabilitado. Falha de construção do renderer 3D também tenta 2D, com diagnóstico explícito. Isso não elimina a necessidade de um desktop para abrir a janela JavaFX.

Troca de modo conserva problema, resultado, seleção, preferências e câmera. O renderer anterior remove handlers, solta câmera/root da SubScene e descarta sua árvore. A aplicação remove seus listeners ao fechar. Não há `AnimationTimer` permanente no produto: câmera, etiquetas e Canvas atualizam por eventos, resize ou nova entrada.

## Demonstrações reais, threads e orçamentos

| Preset | N | Seed | Nós máximos | Resultado observado no Fedora | Custo | Nós observados |
| --- | --- | --- | --- | --- | --- | --- |
| TRIANGLE | 3 | 23 | 10000 | CONCLUIDO | 1828 | 5 |
| OUTPOSTS (inicial) | 5 | 23 | 50000 | CONCLUIDO | 2875 | 65 |
| VALLEY | 8 | 657 | 100000 | CONCLUIDO | 3574 | 7151 |
| FRONTIER | 12 | 23 | 50000 | LIMITE_ATINGIDO, com incumbente | 6362 | 50000 |

Todos usam gerador controlado, até 100000 candidatos e **16 MiB de pico estimado**, com `GenerationCancellationToken`; esse orçamento é o modelo do gerador, não limite da memória do aplicativo. Busca usa **backtracking**, `SolverPreflight` e limite adicional de **2000 ms** com `SolverCancellationToken` distinto. Não há força bruta ilimitada em N=12. A tabela é observação real, não garantia de duração/conclusão em outra máquina; se o tempo expirar antes, o resultado efetivamente recebido é preservado.

Geração, busca, validação e preparação pura do terreno rodam no executor. Somente a FX Application Thread cria/muda/descarta Nodes, malhas, SubScenes, materiais e controles; inclusive os Labels são criados em `start`, não no construtor de `Application`. Nenhum objeto FX mutável atravessa o worker.

Cada solicitação incrementa uma versão. Trocar preset invalida a versão anterior, cancela ambos os tokens e o Future; tanto a conclusão no worker quanto a entrega já enfileirada conferem a versão. Fechar/cancelar também invalida entregas; `shutdownNow` solicita interrupção sem aguardar tarefa na thread FX. O sinal de interrupção não é limpo pelo loader. Os testes usam executor/fila controlados e barreiras para comprovar essas corridas, sem sleeps. Timeout de um teste concorrente é somente guarda contra deadlock, nunca condição de sucesso.

Isso é um carregador mínimo de catálogo, não o fluxo operacional da #12: não há configuração livre de N/seed/solver, HUD completo, reprodução didática ou controlador geral iniciar/cancelar/reexecutar.

## Reprodução no Fedora

JDK 21, Maven 3.9+, desktop e bibliotecas JavaFX/GTK/driver. Dependências Maven são resolvidas no build. Na raiz:

```bash
mvn clean install
mvn -f tsp-app/pom.xml javafx:run
mvn -f tsp-app/pom.xml javafx:run -Djavafx.args="--demo=8"
mvn -f tsp-app/pom.xml javafx:run -Djavafx.args="--demo=12 --simplified --no-decoration"
mvn -f tsp-app/pom.xml javafx:run -Djavafx.args="--2d --demo=5"
```

Coleta opcional, Python 3 e ambiente Linux com display acessível:

```bash
python3 tools/review_world.py
python3 tools/review_world.py --software-2d --output tsp-app/target/review-forced2d
python3 tools/review_world.py --software-auto --output tsp-app/target/review-fallback
```

O script compila `test-compile` com dependências, usa os JARs JavaFX do Maven e abre a mesma composição de produção. `--maven-repo` aceita repositório local fora de `~/.m2/repository`. A coleta faz snapshots da Scene de uma janela visível, após barreiras de pulsos; salva PNG sem edição, `manifest.tsv`, `resources.tsv`, `environment.json` e `review.log`. O manifesto registra N/seed/estado/custo/nós/rota/seleção/modo/resolução. Há verificações dos handlers com eventos sintéticos, troca de modo, descarte da SubScene, bounds/colisão dos Labels reais e atualização de resultado sem reconstruir terreno. Nenhum desses executáveis gráficos participa do Surefire/CI.

O roteiro padrão também apresenta **fixtures de UI** para CANCELADO/LIMITE/ERRO com e sem incumbente, usando fábricas validadas e motivo explícito de fixture. São testes de apresentação, não evidência de que o solver sofreu todas essas interrupções nessa execução. N=12 é a interrupção real do catálogo. No TSV, `-1` indica métrica ausente; na UI aparece “não informado”. `resources.tsv` contém contagens da árvore efetiva, teto geométrico estimado para primitivas, buffers numéricos, heap e RSS pontuais. Não mede frames apresentados, aquecimento, p95 ou estabilidade prolongada de memória.

Para tentar entrada nativa, exclusivamente numa sessão desktop sob seu controle:

```bash
python3 tools/review_world.py --native-input --output tsp-app/target/review-native
```

Esse modo move o cursor, arrasta/clica e envia atalhos à janela. No Wayland disponível, a tentativa não entregou os eventos esperados e falhou; o depurador mostrou janela sem foco e câmera inalterada, sem atingir o handler de arrasto. Não é relatada como teste nativo aprovado. Os controles devem ser homologados com mouse/teclado humanos no roteiro abaixo; sessões X11 podem ter comportamento diferente, ainda não comprovado aqui.

## Evidências, cobertura e limites

[Galeria atual de textura, manifestos e ambiente reais](evidencias/issue-11/vegetacao/README.md), Fedora 44 / kernel 7.2.11, NVIDIA RTX 5060 / driver 615.78.08, Temurin 21.0.12.1, Scene **1380×870**, viewport 3D **1008×717** na configuração padrão. O roteiro inclui panorama, topo, inspeção da BASE e das três variantes, 2D, simplificação, N=3/8/12, rótulos reduzidos, estados/ausências e fallback automático por software sem Scene3D. A execução por software verifica também o botão 3D desabilitado. Isso não equivale a testar todos os drivers ou o caso de falha de inicialização nativa de uma SubScene já suportada. A [primeira galeria](evidencias/issue-11/README.md) e o [primeiro refinamento](evidencias/issue-11/refinamento/README.md) ficam preservados como histórico.

63 testes novos nesta PR (38 iniciais + 25 do refinamento), **67 no app e 1844 no produto** (1652 núcleo + 125 CLI + 67 app). Incluem 12 combinações N=3/5/8/12 × seeds 0/23/657, limites parciais, todos os estados terminais, ausência de rota/métricas, orientação recebida, custos zero/MAX, matriz divergente, imutabilidade, XYZ `int` extremos, extensões/projeções/segmentos degenerados, malhas válidas, enquadramento paisagem/retrato, polo da câmera, conexões limitadas, reutilização do terreno e solicitações obsoletas. O refinamento verifica textos exatos, transições, colisões/prioridades/ordem determinística dos rótulos, variantes/âncoras, orçamento, normais, continuidade do relevo e costura exata com o horizonte. O baseline de 1781 foi preservado. A spike mantém outros 20 testes separados.

```bash
mvn -pl tsp-app -am test
mvn clean verify
git diff --check
```

### Orçamentos e observações de recursos

| Recurso (catálogo N≤12) | Orçamento | Observação na coleta real |
| --- | --- | --- |
| Triângulos centrais | 12800 normal / 2048 simplificado | Mais 5120 / 1024 do anel distante |
| Cena inteira | ≤50000 como teto incluindo primitivas | N=12 parcial: **44213**; N=5: **37474**, simplificado sem decoração **7214** |
| Nodes do viewport (inclui skins/labels/SubScene, exclui painel lateral) | ≤800 | N=12: **260**; N=5: **84**; Canvas 2D: **4** |
| Materiais compartilhados | ≤20 | **16–18** nos casos capturados; albedo de rocha compartilhado |
| Sítios decorativos | ≤32 | Até 24 candidatos normal / 7 simplificado, com exclusão de cidades |
| Vegetação | ≤384 candidatos, filtrados por inclinação e distância | N=12: 224 elementos; N=5: 271, reduzidos a 44 no simplificado |
| Buffers numéricos + albedo compartilhado | ≤32 MiB por qualidade | N=12: **7980092 B**, N=5 normal **7811792 B**, simplificado **5681984 B** |

O teto de triângulos de Cylinder/Sphere é conservador; `resources.tsv` e o runner documentam a contagem. O orçamento de buffers **não é limite de heap/RSS**: exclui listas/records preparados, segunda qualidade, toolkit, mipmaps/cópias nativas, snapshots e driver. Na sequência de 21 capturas com a nova textura, heap observado foi **87–453 MiB**, RSS **442864–795708 KiB**. São amostras pontuais durante carregamentos/trocas e snapshots completos, sem GC ou aquecimento controlado; não provam memória estabilizada nem ausência de vazamento. Não foi feito benchmark sustentado de FPS apresentados ou latências p95; não há promessa de 30 FPS. Tempo do solver não mede renderização.

Cidades e ciclo continuam O(N), rótulos limitados a 48 com prioridade da seleção e BASE 0, até 32 arestas opcionais, nunca grafo completo. Há malha distante fixa, sem LOD contínuo ou instancing. Renderização para N grande, importação GLB, empacotamento de runtimes e efeitos avançados não foram homologados. Pontos lógicos próximos/coincidentes podem compartilhar posição e arquitetura aparente; lista e 2D continuam disponíveis. Hardware fraco pode preferir simplificação ou 2D.

### Aceite comprovado e revisão ainda necessária

- Comprovado por testes headless: fidelidade de cidades, matriz, sequência/fechamento, estados, ausência, transformações, casos degenerados, geometria, enquadramento matemático e invalidação/cancelamento dos carregamentos.
- Comprovado em janela real Fedora: renderização/capturas dos presets, seleção e topo por API, alternância 3D/2D mantendo seleção, simplificação/decoração, fallback automático e forçado por software. Handlers de órbita/zoom foram exercitados por eventos sintéticos; isso é distinto da tentativa nativa inconclusiva.
- Pendente de revisão humana (#13/#17): arrastar e orbitar em ambos os eixos, usar roda até os limites e reset, clicar abrigos/etiquetas, focar pontos extremos, conferir identificadores/XYZ/rota, trocar presets rapidamente e fechar durante carregamento, comparar topo/2D e simplificação, testar resize e legibilidade em notebook. Registrar hardware/resolução e travamentos; medir desempenho de apresentação separadamente se necessário.
- Pendente: janela real Windows, hardware 3D fraco e revisão artística/conforto pelo responsável. CI Ubuntu/Windows comprova build/testes, não aparência, FPS ou usabilidade. #12 e aceite final de #13 não estão concluídos por esta entrega.
