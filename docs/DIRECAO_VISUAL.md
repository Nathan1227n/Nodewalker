# NodeWalker — direção visual e experiência 3D
**Status:** direção de design do G3; JavaFX 3D aceito para o MVP após PR #33. Mapa real implementado na issue #11; HUD/fluxo operacional #12, homologação #13/#17 e efeitos #15 continuam separados. Contratos e evidências: [VISUALIZACAO_TSP.md](VISUALIZACAO_TSP.md).
**Referências de rastreio:** issues #11 (mapa), #12 (UI/HUD), #13 (aceite), #15 (polimento), #23 (decisão de engine) e #17 (verificação 2D/Windows).  
**Fonte da verdade algorítmica:** DOC 02, DOC 03, `tsp-core` e testes.

## 1. Visão do produto
**NodeWalker: otimização de rotas entre os últimos refúgios de um mundo pós-colapso.**

O usuário deve sentir escala, solidão, terreno inóspito e o contraste entre paisagem hostil e pequenos centros seguros. A entrega continua sendo uma ferramenta visual que explica e compara algoritmos exatos de TSP, **não um jogo de exploração ou simulação de física**.

**Inspiração:** atmosfera de travessia e isolamento de ficção científica pós-apocalíptica (inclusive obras como *Death Stranding*), sem reproduzir marcas, personagens, UI, composição exata ou assets protegidos. Conceber linguagem visual própria.

**Experiência principal:** explorar a rede de refúgios em 3D, gerar uma instância determinística, escolher solver, ver rota ótima comprovada e métricas auditáveis; a alternativa 2D deve permanecer útil.

## 2. Identidade artística
| Elemento | Direção | Necessidade no G3 |
| --- | --- | --- |
| Terreno | Montanhas rochosas, vales, platôs e grandes vazios; heightmap ou malha com LOD/complexidade controlada | Sim: versão simplificada |
| Ruínas | Antenas, torres, estruturas industriais abandonadas discretas; sem esconder nós ou rota | Sim: poucos props |
| Atmosfera | Céu nublado frio, sombra dessaturada, neblina leve com controle de intensidade | Sim, se não reduzir legibilidade/desempenho |
| Refúgios | Pequenos complexos com beacon vertical e foco de luz âmbar/ciano; cidade índice 0 especialmente clara | Sim |
| Rota | Segmentos 3D luminosos destacados sobre o cenário; origem e retorno explícitos | Sim |
| HUD | Painéis técnicos escuros, tipografia legível, informação hierarquizada e métricas reais | Sim |
| Movimento | Câmera orbital, foco, zoom, vista superior, reset | Sim |
| Cinema | Flyover, animação de pulsos, chuva, volumetria pesada, partículas de atmosfera | Opcional G5 |

### Paleta e composição do MVP (ajustadas após capturas reais)
- Plano de fundo e sombras: `#0A1119`, `#15222B`, `#26343A`.
- Rocha/terreno: grafite `#252C2B`, ardósia `#353C38`, verde-acinzentado `#474E44`, pedra `#5D655B`, cristas `#777E73`, picos `#9AA095`. Transições contínuas, normais suaves e horizonte dessaturado.
- Refúgio central: âmbar `#FFBE64` e branco quente `#F5E5C2`.
- Refúgios secundários: ciano frio `#58DAE5`, discreto em pequenos pontos; arquitetura de concreto, metal, ferrugem e painéis escuros.
- Rota ótima concluída: ciano/branco luminoso; incumbente parcial: contraste distinto e legenda explícita.
- Alertas (limite, erro, cancelado): padrões de ícone e texto; **não depender apenas de cor**.

Priorizar cenários legíveis em resolução de notebook e em 2D; o fundo não pode reduzir contraste da rota.

A região se estende além da instância, com vales compartilhados e cristas periféricas. A câmera continua enquadrando as cidades; ampliar a decoração não reduz arbitrariamente o grupo TSP. O terreno não tem saia vertical de ilha. A [correção de textura](TEXTURAS.md) usa rocha fosca de basalto/ardósia gerada para o projeto, iluminação mais escura e normais consistentes; o gradiente fica somente no fundo. Não se alega névoa física, bloom, sombras projetadas ou PBR. [Capturas Fedora atuais](evidencias/issue-11/texturas/README.md).

Vegetação da continuação de 18/10: bosques de copas irregulares, galhos mortos, arbustos, capim e rochas quebradas, em posições determinísticas e sem ocupar âncoras de refúgios. Folhagem usa textura local própria; a decoração pode ser ocultada e simplificada. [Cena atual com vegetação](evidencias/issue-11/vegetacao/README.md).

## 3. Linguagem dos refúgios
Cada `City` é um **nó seguro**, não necessariamente uma cidade construída em tamanho real.
- **Índice 0:** estação central/terminal de partida; maior beacon, cor distinta e etiqueta “BASE 0”.
- **Índices 1..N-1:** abrigos/estações de sobrevivência, cada qual com rótulo persistente ou configurável, identificação em seleção e leitura de XYZ lógico.
- Seleção mostra índice, X, Y, Z, custo das ligações quando consultado e posição na rota encontrada.
- Geometrias devem escalar com zoom/distância para não desaparecerem entre montanhas; usar marcador vertical/billboard se necessário.

Os refúgios não são novos vértices artificiais: há exatamente N cidades e a rota usa exatamente os índices de `TspRoute`.

No MVP, BASE 0 combina comando modular, ala de serviço, plataforma, torre/radar, painéis e gerador. Três variantes secundárias repetíveis representam comunicação, manutenção/suprimentos e monitoramento. Componentes são agrupados em malhas por material. Props periféricos não recebem beacon/índice de cidade e podem ser ocultados. Rótulos usam prioridade de selecionado/BASE 0, evitam colisão e possuem modo reduzido; a lista lateral conserva acesso a todos os índices.

## 4. Fidelidade matemática obrigatória
O projeto corrente **não calcula caminhos pedestres seguindo montanhas**. A distância lógica vem da matriz de `TspProblem`, calculada pelo gerador a partir de segmentos euclidianos 3D e arredondada conforme DOC 03. Isto não muda com o tema.

- Dados imutáveis `City(x,y,z)` e `TspProblem.cost(i,j)` são fonte da verdade.
- O renderizador aplica transformações explícitas do sistema de eixos (por exemplo, transformar XYZ lógico em coordenadas de mundo visual X,Y,Z com escala/translação comuns); manter inversão/escala inspecionáveis.
- `y` lógico representa posição vertical no espaço TSP. A malha do terreno deve acomodar os pontos ou representar o abrigo visualmente com uma base/torre; **não alterar o y lógico para “grudar” no terreno** nem recalcular a matriz a partir da posição ajustada.
- Renderizar a solução como conexões 3D diretas entre refúgios, potencialmente acima/através do relevo em algumas vistas. Não chamá-las de “estradas físicas” com custo topográfico.
- Uma eventual navegação por trilhas montanhosas seria outro modelo de custo, outra especificação e outros testes, nunca mudança silenciosa.
- Destacar `CONCLUIDO` como ótimo apenas após solver correto; `CANCELADO`, `LIMITE_ATINGIDO`, `ERRO` exibem eventual incumbente como **melhor rota conhecida, não ótima certificada**.

## 5. Câmera, leitura e controles
**Mínimo para G3:** órbita ao redor da instância, zoom com limites, pan/foco, reset, vista de topo e seleção de abrigo. Oferecer toggle para conexões candidatas, decoração/ruínas, neblina e rótulos.

Evitar cruzamentos aparentes confusos por perspectiva: destacar caminho completo, origem e direção com numeradores/ícones ou segmentação visual. No grafo completo, mostrar poucas arestas secundárias ou reduzir muito sua opacidade; a densidade cresce em `O(N²)`.

A rota escolhida pode receber pulso simples em G3 **apenas se custo baixo**; animação de flyover, clima e sequências cinematográficas ficam para G5.

## 6. HUD e fluxos
**Previsto na #12:** algoritmo, N, seed, estado, custo da incumbente, indicador de ótima comprovada, rota, tempo, nós visitados, rotas completas avaliadas, folhas descartadas, espaço teórico `(N-1)!`, limites ativos, iniciar/cancelar/reexecutar. A #11 entrega um catálogo limitado e painel de apresentação para demonstrar o mapa; não implementa esse fluxo operacional completo. Métrica ausente aparece como “não informado”, nunca zero inventado.

Fluxo separado: **configuração → preflight da geração → geração (assíncrona) → preflight do solver → busca (assíncrona) → resultado/visualização**. A geração controlada, sob implementação separada na Etapa B, deve proteger contra domínio finito, orçamento de tentativas e memória `O(N²)`; a issue #9 controla a busca (tempo/nós). Não confundir os dois cancelamentos.

Sincronização: geração, busca e preparação pura de geometria em background; criação/mutação/descarte de Nodes e recursos JavaFX somente na FX Application Thread. Entregas obsoletas são invalidadas por identificador. A #12 reutilizará o contrato de apresentação sem colocar solvers no renderizador.

## 7. Tecnologia e estratégia de risco
**Atual:** Java 21 + Maven multimódulo, `tsp-core` separado de `tsp-app`; **JavaFX 3D ACEITA PARA O MVP**, com Canvas 2D independente. [ADR 0001](ADR/0001-renderizador-3d.md). Integração, manutenção, fallback e prazo justificam a escolha; não se afirma superioridade gráfica/FPS sobre JME.

A PR #33 entregou os protótipos isolados e o [relatório histórico comparativo](SPIKE_RENDERIZADORES.md). Eles permanecem fora do reactor de produção. JME continua alternativa futura, não dependência do aplicativo.

Build/CI não homologam aparência ou FPS. O mapa tem capturas reais Fedora e amostras pontuais de recursos; navegação humana, desempenho sustentado e Windows gráfico continuam em #13/#17.

## 8. Asset pipeline e licenças
- O MVP usa malhas autorais preparadas em Java puro; Blender é opção futura de modelagem/conversão offline. JavaFX 21 não importa glTF/GLB nativamente.
- Usar heightmaps gerados proceduralmente ou autorizados; terreno e props devem ser leves, com malhas/colisões simplificadas se necessário.
- Consultar bibliotecas como Poly Haven para texturas/HDRI **conferindo licença por asset**; preferir CC0. Guardar autores/licenças em arquivo de créditos se usar assets externos.
- Não inserir assets, screenshots, personagens, logotipos ou modelos extraídos de *Death Stranding* ou outros jogos.
- Evitar dependência de downloads externos em tempo de execução ou assets grandes que prejudiquem o clone limpo e o CI.

**Estrutura implementada** (nenhum novo módulo de produção):
```text
tsp-core/                 lógica TSP (inalterada)
tsp-app/.../world/        apresentação, transformações e geometria pura
tsp-app/.../world/render/ adaptadores JavaFX 3D e Canvas 2D
spikes/renderer-comparison/ protótipos históricos isolados
docs/DIRECAO_VISUAL.md
docs/ADR/0001-renderizador-3d.md
```

[Pesquisa por asset, autor, licença, formatos, polígonos, materiais e tamanho](ASSETS_VISUAIS.md). Nenhum candidato externo foi adotado nesta PR; importação e preservação de materiais não são presumidas.

## 9. Execução por gates e cortes de escopo
| Etapa | Entrega | Prioridade |
| --- | --- | --- |
| G1/G2 | Solvers corretos, contratos, cancelamento e auditoria | P0; **não bloquear por arte** |
| Spike #23 | Comparar os renderizadores e decidir com evidências | P1, timebox de poucas horas |
| G3 #11 | Mapa com relevo básico, ruínas discretas, refúgios, rota, câmera e fallback 2D | P1, visual essencial |
| G3 #12 | HUD, preflight, execução assíncrona, estados e geração protegida | P1 |
| G3 #13 | Demo reproduzível e revisão do visual + exatidão | P0 |
| G5 #15 | Chuva, partículas avançadas, flyover, iluminação mais complexa | P3, opcional |

**Corte se houver risco de atraso para 19/10:** diminuir a complexidade de materiais, props, névoa e efeitos; não retirar identificação de nós, fidelidade da rota, câmera mínima, modo 2D ou os solvers exatos.

## 10. Critérios de aceite visuais
- [ ] Coordenadas e índices de todos os N refúgios correspondem à instância; nenhuma cidade oculta permanentemente.
- [ ] Origem 0 visivelmente distinta e rótulos/tooltip úteis.
- [ ] Rota segue `TspResult.route()`, incluindo retorno à origem; custo mostrado vem do problema.
- [ ] Estado de ótimo certificado difere visualmente de incumbente interrompida.
- [ ] Relevo e props simples criam atmosfera pós-colapso sem impedir leitura do grafo.
- [ ] Órbita, zoom, reset, vista superior e seleção funcionam no Fedora.
- [ ] A alternativa 2D continua útil e compatível com a arquitetura escolhida.
- [ ] Cena responde com N representativo; efeitos podem ser reduzidos quando necessário.
- [ ] Limites/cancelamento de geração e busca não congelam a interface.
- [ ] `mvn clean verify` e CI continuam verdes, sem JavaFX/jMonkeyEngine no `tsp-core`.
- [ ] Documentação de licenças e diferenças entre distância TSP e rota topográfica está acessível.

## 11. Decisões e questões ainda abertas
1. Engine do MVP decidida: JavaFX 3D, HUD na mesma janela e fallback 2D independente.
2. Altitudes lógicas preservadas por transformação uniforme; fundações decorativas podem ser altas em projeções próximas. Não mover cidades para melhorar uma captura.
3. Arquitetura e props autorais entregues; pipeline externo só após benefício demonstrado e homologação de licença/importação.
4. Homologar conforto, resize, leitura em notebook e janela Windows; medir frames apresentados/memória estabilizada separadamente se necessário.
5. #12 implementará configuração e controle operacional; #13/#17 homologam o conjunto. Efeitos caros e refinamentos cinematográficos adicionais continuam na #15.

## 12. Estado após PRs #34–#37 — 21/10/2026

Os itens futuros da seção 11 preservam o planejamento da época. Mapa, painel assíncrono e pesos/decomposição estão mesclados. A [coleta #13](VALIDACAO_MVP.md) aprovou 14 roteiros obrigatórios de janela Fedora, com seleção/câmera por eventos/API, 3D/2D, custos, resultados, cancelamento e fechamento. [Evidências atuais](evidencias/issue-13/README.md). O checklist da seção 10 não foi marcado como homologação humana integral: conforto com mouse/teclado e Windows real seguem pendentes.

Não houve remodelagem, importação de assets, efeitos novos ou medição de FPS apresentado nesta validação. Procedência e hashes dos dois PNGs locais foram conferidos; nenhum download ocorre em runtime. Oclusão do overlay pelo terreno continua sem teste de profundidade, com tabela completa disponível. A data 19/10 era o prazo planejado; 21/10 é a data desta coleta, ainda sem aceite humano final.
