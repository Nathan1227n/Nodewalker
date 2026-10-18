# Texturas, atmosfera e vegetação — #11

Continuação da PR #34 após a captura de 17/10/2026. O aspecto anterior vinha de uma combinação de normais ponderadas por triângulos muito alongados no horizonte, ondulação de alta frequência nas montanhas e um gradiente de cor usado como se fosse textura. O brilho especular acentuava as faixas. Não era um erro de coordenadas ou do solver.

## Implementação

- Normais obtidas pela derivada da mesma função de relevo, independentes da resolução do grid. Terreno central e horizonte agora compartilham também as normais da costura, sem a mudança brusca de iluminação. Removida a ondulação de alta frequência multiplicada nos picos.
- `SurfaceMapping` escolhe o plano dominante de cada triângulo: XZ nos planos horizontais, XY/YZ nas paredes. A textura acompanha unidades de mundo, inclusive altitude nas encostas, em vez de ser esticada por um gradiente. Tiling de 240 unidades no terreno, 36 nas estruturas. É projeção por plano dominante; não é um shader de blending triplanar e podem existir transições entre orientações.
- Rocha de basalto/ardósia fosca, com fissuras, cascalho e variação mineral discreta. Material de terreno sem componente especular; concreto e rochas decorativas compartilham a imagem. Luz fria menos intensa e céu mais escuro, mantendo os beacons e a rota em contraste.
- O Canvas 2D usa a mesma textura como fundo discreto, com relevo tonal de baixa opacidade. Não cria nenhum recurso Scene3D.
- Duas imagens carregadas sob demanda na FX thread, compartilhadas durante a vida do aplicativo: rocha 1024² e folhagem 512² (cerca de 5 MiB RGBA no total). Não há download em runtime, importador ou nova dependência. Nodes e materiais continuam exclusivos da FX thread; preparação de superfície, vegetação e normais continua no worker.

Índices, XYZ lógicos, transformação uniforme, posições dos marcadores e segmentos, ciclo, custos e estados dos solvers permanecem intactos. `tsp-core` não foi alterado. Não se alega PBR, normal mapping, bloom, névoa física ou shadow mapping.

## Origem e reprodução do asset

Arquivo: [`basalt-slate-albedo.png`](../tsp-app/src/main/resources/textures/basalt-slate-albedo.png), **1254×1254 RGB**, **4018800 bytes**. Carregado em **1024×1024** para o produto: aproximadamente 4 MiB RGBA decodificados, compartilhados, além das cópias/overheads de toolkit e driver. O source original é preservado sem edição. SHA-256:

```text
35be47ff46ba484a64afd4757513da2ba93725134ebe9c7369adbafceadfafb8
```

Imagem criada para este projeto com a skill `imagegen`, pelo **tool integrado**, sem CLI/API própria e sem incorporar modelos ou imagens de bibliotecas de terceiros. A origem é geração por IA; não é um download CC0 e não deve ser confundida com os candidatos pesquisados em [ASSETS_VISUAIS.md](ASSETS_VISUAIS.md). O modelo retornou 1254×1254 apesar do tamanho pedido; o carregamento limita a resolução usada. A geração não é determinística por seed; o PNG versionado e seu hash reproduzem o asset efetivamente entregue.

Prompt integral:

```text
Use case: photorealistic-natural. Asset type: seamless square tileable diffuse/albedo texture for rocky 3D terrain in a JavaFX desktop application. Primary request: create a beautiful realistic dark weathered basalt and slate surface, melancholy post-collapse mountain landscape, extremely low saturation cool charcoal gray with subtle muted green mineral patches. Composition: orthographic flat surface filling the entire square edge to edge; no perspective, no objects, no horizon. Fine natural rock grain, small irregular fissures, layered mineral structure, scattered tiny scree fragments integrated into the surface; varied organic multiscale detail without a dominant directional stripe or recognisable repeating feature. Lighting: flat neutral diffuse illumination baked as little as possible, no highlights, no cast shadows, no vignette, no bright white cracks; this is an ALBEDO map, not a rendered landscape. Seamlessly tileable on all edges. Medium dark overall, enough midtone detail to remain readable under dim cold lighting. Output one square 1024 by 1024 texture only. No grid, no atlas, no text, no watermark, no logo, no UI.
```

## Vegetação pós-colapso — adição de 18/10/2026

`VegetationFactory` prepara oito regiões de bosques e vegetação rasteira, com até 384 candidatos determinísticos. Árvores com copas irregulares e galhos, troncos mortos, arbustos, tufos de capim, manchas de cobertura vegetal e rochas compartilham seis malhas por material, sem um Node para cada folha/planta. As rochas também usam volumes irregulares, substituindo a geometria semelhante a cabines.

A inclinação do relevo filtra os locais de plantio; todas as plantas mantêm ao menos 120 unidades XZ dos pontos lógicos de cidade. Altura de apoio vem da interpolação dos triângulos da região central realmente desenhada, evitando plantas flutuando sobre uma aproximação analítica diferente. Árvores podem ocultar parte de uma construção sob certas perspectivas: orbitar ou **D / Vegetação, rochas e ruínas** permite inspecionar o mesmo ponto; nenhuma cidade é deslocada.

O modo simplificado conserva um subconjunto dos mesmos IDs/XZ/alturas, com menos detalhes. A altura decorativa de apoio pode acompanhar a diferença entre as malhas detalhada/simplificada; as âncoras TSP são idênticas. Todo o conjunto é estático, preparado uma vez por instância/qualidade; não há física, vento animado, partículas ou reconstrução por frame. O Canvas representa vegetação em tons discretos e mantém os marcadores de cidades distintos.

Segundo asset: [`weathered-evergreen-albedo.png`](../tsp-app/src/main/resources/textures/weathered-evergreen-albedo.png), 1254×1254 RGB, **4251555 B**, carregado em 512×512. Gerado pelo mesmo tool integrado `imagegen`, sem alteração do source e sem importação de um pacote externo. SHA-256:

```text
4e1c405c24919e76450336f8cbaa6b3a9ee760f0db189b3f918c6bb709a09957
```

Prompt da folhagem:

```text
Asset type: seamless square diffuse/albedo texture for foliage on low-poly tree canopies and undergrowth in a dark post-apocalyptic mountain map. Top-down orthographic close-up of a dense interlocking surface of small weathered evergreen leaves, muted olive, gray-green, forest green, occasional dry tan needles and tiny brown twigs. Natural organic irregularity and detailed leaf veins, much smaller than the image, no one dominant leaf or object. Flat neutral diffuse illumination, no directional shadows, no glossy highlights, no vignette. Moderately dark desaturated colors but readable midtone leaf details. The entire square must be filled with foliage with no sky, no ground, no perspective and no transparent holes. Seamlessly tileable all sides. One 1024x1024 albedo tile, no text, no watermark, no atlas, no normal map.
```

## Validação do conjunto atual

Os testes headless verificam UVs em planos horizontais, verticais e muito inclinados, área mínima de projeção, coordenadas negativas, degeneração, estabilidade entre tamanhos de faces, tamanho de tile inválido, decodificação do PNG empacotado e normais idênticas na costura. Os testes existentes continuam conferindo âncoras, segmentos, estados, limites e matemática.

[Capturas atuais com vegetação e dados brutos](evidencias/issue-11/vegetacao/README.md); [rodada de textura anterior à vegetação](evidencias/issue-11/texturas/README.md). O catálogo N=3/5/8/12 manteve os custos/rotas/estados anteriores, incluindo LIMITE_ATINGIDO em 50000 nós para N=12/seed 23. O fallback por software passou sem Scene3D. Capturas não são medição de FPS, memória estabilizada ou teste humano de navegação; homologação Windows gráfico e hardware fraco permanece pendente.

```bash
mvn clean install
mvn -f tsp-app/pom.xml javafx:run -Djavafx.args="--demo=12"
python3 tools/review_world.py --output tsp-app/target/texture-review
python3 tools/review_world.py --software-auto --output tsp-app/target/texture-fallback
```

O modo simplificado conserva a aparência fosca e a mesma textura compartilhada, reduzindo geometria. Em máquinas sem Scene3D, use Canvas 2D. Os registros das primeiras rodadas ficam preservados como histórico, sem substituir suas imagens por esta correção.

O JAR de `tsp-app` observado após `clean verify` tem aproximadamente **8,1 MB**, incluindo os dois PNGs locais; não inclui JavaFX/runtime nem o JAR separado de `tsp-core`. Nenhum download adicional é necessário para os materiais durante a execução.
