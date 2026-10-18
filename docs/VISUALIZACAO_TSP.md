# Mapa real de refúgios — issue #11

Implementação em `tsp-app`, sobre o merge `29fc04c` da PR #33. JavaFX 3D está **ACEITA PARA O MVP** na [ADR 0001](ADR/0001-renderizador-3d.md), por integração, prazo, manutenção e fallback. A decisão não afirma superioridade gráfica ou de FPS sobre jMonkeyEngine. O [relatório histórico da spike](SPIKE_RENDERIZADORES.md) e seus módulos permanecem preservados.

## Arquitetura e entrada de apresentação

| Camada | Contrato implementado |
| --- | --- |
| Fonte de verdade | `TspProblem`, `City`, `TspResult`, `TspRoute` do núcleo, sem alterações |
| Modelo | `world.TspSceneModel.of(problem, Optional<TspResult>)`: dados e coleções imutáveis, exatamente N refúgios, rota recebida, metadados e terreno preparado |
| Matemática visual | `WorldTransform`, `MapProjection`, `SegmentGeometry`, `OrbitCamera`, `Vec3`: Java puro, sem toolkit ou janela |
| Geometria | `TerrainFactory` → `TerrainData`: malhas autorais detalhada e simplificada, preparadas no worker |
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

`withResult(Optional<TspResult>)` compartilha os terrenos da mesma instância. Atualizações de resultado da mesma referência de `TspProblem` preservam a câmera/seleção, o terreno e a SubScene; substituem somente as conexões da rota/seleção. O controlador da #12 deve vincular resultados à instância/executação correta. `TspResult` não armazena identidade de problema: o modelo confere tamanho, ciclo e custo contra a matriz fornecida, mas não pode detectar duas instâncias diferentes com ciclos e custos coincidentes.

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
| `optimalityGuaranteed()==true` | ÓTIMO CERTIFICADO PELO SOLVER, rota contínua ciano clara |
| CANCELADO / LIMITE_ATINGIDO / ERRO com incumbente | Estado original e motivo, MELHOR ROTA CONHECIDA · NÃO ÓTIMO CERTIFICADO, rota âmbar tracejada |
| Interrupção/erro sem incumbente | Sem rota desenhada; nenhum ciclo fictício |
| Custo/métrica ausente | “não informado”, sem substituir por zero |
| Custo `Long.MAX_VALUE` | Valor legítimo, formatado como inteiro; nunca sentinela |

A certificação replica a declaração contratual de `TspResult`; o visualizador não realiza uma nova prova de ótimo. Métricas parciais permanecem as observadas; não exige cobertura fatorial nem fabrica descartes. Erro de geração/carregamento é apresentado separadamente, sem fabricar `TspResult.ERRO`, CANCELADO ou LIMITE_ATINGIDO.

## Cena, navegação e fallback

Terreno de rocha facetada, vales e cristas, prateleiras locais e corte geológico; seis materiais, ambiente frio e duas luzes direcionais globais. BASE 0 é um terminal maior com pórtico/asa de serviço, beacon âmbar e identificação própria; secundários têm variantes de abrigo e beacons ciano. Ruína/antena e rochas são autorais. Emissão dos materiais e halo simples tornam a rota legível; não há sombras caras, PBR, volumetria, chuva ou partículas densas. As etiquetas são overlays com linhas de chamada e fundo de contraste.

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
| Arestas secundárias | Caixa de seleção: até 32 ligações do selecionado, omitindo as da rota |

Reset/topo consideram os limites reais da instância e o aspecto do viewport. Near/far são proporcionais à cena. Zoom/foco podem tirar outros pontos do campo visual de propósito; reset recupera o conjunto. Mudanças de tamanho atualizam a câmera sem depender de cinco posições fixas.

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

O script compila `test-compile` com dependências, usa os JARs JavaFX do Maven e abre a mesma composição de produção. `--maven-repo` aceita repositório local fora de `~/.m2/repository`. A coleta faz snapshots da Scene de uma janela visível, após barreiras de pulsos; salva PNG sem edição, `manifest.tsv`, `environment.json` e `review.log`. O manifesto registra N/seed/estado/custo/nós/rota/seleção/modo/resolução. Há verificações dos handlers com eventos sintéticos, troca de modo, descarte da SubScene e atualização de resultado sem reconstruir terreno. Nenhum desses executáveis gráficos participa do Surefire/CI.

Para tentar entrada nativa, exclusivamente numa sessão desktop sob seu controle:

```bash
python3 tools/review_world.py --native-input --output tsp-app/target/review-native
```

Esse modo move o cursor, arrasta/clica e envia atalhos à janela. No Wayland disponível, a tentativa não entregou os eventos esperados e falhou; o depurador mostrou janela sem foco e câmera inalterada, sem atingir o handler de arrasto. Não é relatada como teste nativo aprovado. Os controles devem ser homologados com mouse/teclado humanos no roteiro abaixo; sessões X11 podem ter comportamento diferente, ainda não comprovado aqui.

## Evidências, cobertura e limites

[Galeria, manifestos e ambiente reais](evidencias/issue-11/README.md), Fedora 44 / kernel 7.2.11, NVIDIA RTX 5060 / driver 615.78.08, Temurin 21.0.12.1, Scene **1380×870**, viewport 3D **1008×717** na configuração padrão. Capturas incluem panorama, topo, inspeção da BASE, 2D, simplificação, N=3/8/12 e fallback automático por software sem Scene3D. A execução por software verifica também o botão 3D desabilitado. Isso não equivale a testar todos os drivers ou o caso de falha de inicialização nativa de uma SubScene já suportada.

38 testes novos, 42 no app, **1819 no produto** (1652 núcleo + 125 CLI + 42 app). Incluem 12 combinações N=3/5/8/12 × seeds 0/23/657, limites parciais, todos os estados terminais, ausência de rota/métricas, orientação recebida, custos zero/MAX, matriz divergente, imutabilidade, XYZ `int` extremos, extensões/projeções/segmentos degenerados, malhas válidas, enquadramento paisagem/retrato, polo da câmera, conexões limitadas, reutilização do terreno e solicitações obsoletas. O baseline de 1781 foi preservado. A spike mantém outros 20 testes separados.

```bash
mvn -pl tsp-app -am test
mvn clean verify
git diff --check
```

Limites conhecidos: malha normal 12800 triângulos / simplificada 2048, seis materiais de terreno; cidades e ciclo O(N), rótulos com densidade limitada e seleção sempre identificada. Até 32 arestas opcionais, nunca grafo completo. A cena não recebe LOD contínuo nem instancing; hardware fraco pode preferir simplificação ou 2D. O relevo/arquitetura é estilo procedural facetado e os pontos muito próximos podem se sobrepor. Renderização sustentada para N grande, importação GLB, empacotamento de runtimes e efeitos avançados não foram homologados nesta PR. Não foram medidos FPS apresentados, latências p95 ou consumo de memória deste novo visualizador; nenhuma promessa de 30 FPS é feita. Tempo do solver não mede renderização.

### Aceite comprovado e revisão ainda necessária

- Comprovado por testes headless: fidelidade de cidades, matriz, sequência/fechamento, estados, ausência, transformações, casos degenerados, geometria, enquadramento matemático e invalidação/cancelamento dos carregamentos.
- Comprovado em janela real Fedora: renderização/capturas dos presets, seleção e topo por API, alternância 3D/2D mantendo seleção, simplificação/decoração, fallback automático e forçado por software. Handlers de órbita/zoom foram exercitados por eventos sintéticos; isso é distinto da tentativa nativa inconclusiva.
- Pendente de revisão humana (#13/#17): arrastar e orbitar em ambos os eixos, usar roda até os limites e reset, clicar abrigos/etiquetas, focar pontos extremos, conferir identificadores/XYZ/rota, trocar presets rapidamente e fechar durante carregamento, comparar topo/2D e simplificação, testar resize e legibilidade em notebook. Registrar hardware/resolução e travamentos; medir desempenho de apresentação separadamente se necessário.
- Pendente: janela real Windows, hardware 3D fraco e revisão artística/conforto pelo responsável. CI Ubuntu/Windows comprova build/testes, não aparência, FPS ou usabilidade. #12 e aceite final de #13 não estão concluídos por esta entrega.
