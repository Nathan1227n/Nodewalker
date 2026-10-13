# NodeWalker — direção visual e experiência 3D
**Status:** proposta de design para G3; implementação condicionada à spike #23 e à preservação do MVP de 19/10/2026.  
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

### Paleta sugerida (ajustável após protótipo)
- Plano de fundo e sombras: `#0A1119`, `#15222B`, `#26343A`.
- Rocha/terreno: `#4A5150`, `#71766F`, verde-acinzentado discreto `#465951`.
- Refúgio central: âmbar `#FFBE64` e branco quente `#F5E5C2`.
- Refúgios secundários: ciano frio `#58DAE5` e azul `#4C9FD6`.
- Rota ótima concluída: ciano/branco luminoso; incumbente parcial: contraste distinto e legenda explícita.
- Alertas (limite, erro, cancelado): padrões de ícone e texto; **não depender apenas de cor**.

Priorizar cenários legíveis em resolução de notebook e em 2D; o fundo não pode reduzir contraste da rota.

## 3. Linguagem dos refúgios
Cada `City` é um **nó seguro**, não necessariamente uma cidade construída em tamanho real.
- **Índice 0:** estação central/terminal de partida; maior beacon, cor distinta e etiqueta “BASE 0”.
- **Índices 1..N-1:** abrigos/estações de sobrevivência, cada qual com rótulo persistente ou configurável, identificação em seleção e leitura de XYZ lógico.
- Seleção mostra índice, X, Y, Z, custo das ligações quando consultado e posição na rota encontrada.
- Geometrias devem escalar com zoom/distância para não desaparecerem entre montanhas; usar marcador vertical/billboard se necessário.

Os refúgios não são novos vértices artificiais: há exatamente N cidades e a rota usa exatamente os índices de `TspRoute`.

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
HUD mínimo: algoritmo, N, seed, estado, custo da incumbente, indicador de ótima comprovada, rota, tempo, nós visitados, rotas completas avaliadas, folhas descartadas, espaço teórico `(N-1)!`, limites ativos, iniciar/cancelar/reexecutar. Métrica ausente deve aparecer como “não informado”, nunca zero inventado.

Fluxo separado: **configuração → preflight da geração → geração (assíncrona) → preflight do solver → busca (assíncrona) → resultado/visualização**. A geração controlada, sob implementação separada na Etapa B, deve proteger contra domínio finito, orçamento de tentativas e memória `O(N²)`; a issue #9 controla a busca (tempo/nós). Não confundir os dois cancelamentos.

Sincronização de UI: nenhuma geração, busca fatorial ou renderização pesada na thread do HUD; render updates agregados. Implementação concreta após decisão de engine.

## 7. Tecnologia e estratégia de risco
**Atual:** Java 21 + Maven multimódulo, `tsp-core` separado de `tsp-app` JavaFX. O Java continua sendo uma escolha válida. O jMonkeyEngine é **candidato**, não decisão tomada.

A **issue #23** deve implementar protótipo isolado que compare:
1. JavaFX 3D estilizado, aproveitando o app atual;
2. jMonkeyEngine 3D com terreno/heightmap, iluminação, câmera e modelos importados.

Medir FPS no Fedora, estabilidade de drivers, consumo de memória, tempo de implementação, pipeline de assets, suporte a Java 21/Maven, e dificuldade real de HUD e fallback 2D. Se embutir motor jMonkeyEngine na janela JavaFX introduzir risco, usar módulos/janelas separados ou preservar JavaFX como renderizador principal. Não afirmar integração antes de testá-la.

**Aceite da spike:** documento `docs/ADR/0001-renderizador-3d.md` com decisão e justificativa, capturas, riscos, resultado comparativo e plano incremental. Até lá, não iniciar uma migração irreversível.

## 8. Asset pipeline e licenças
- Modelar props autorais (torres, refúgios, antenas) em Blender; exportar como **glTF/GLB** quando compatível com o motor escolhido.
- Usar heightmaps gerados proceduralmente ou autorizados; terreno e props devem ser leves, com malhas/colisões simplificadas se necessário.
- Consultar bibliotecas como Poly Haven para texturas/HDRI **conferindo licença por asset**; preferir CC0. Guardar autores/licenças em arquivo de créditos se usar assets externos.
- Não inserir assets, screenshots, personagens, logotipos ou modelos extraídos de *Death Stranding* ou outros jogos.
- Evitar dependência de downloads externos em tempo de execução ou assets grandes que prejudiquem o clone limpo e o CI.

**Estrutura candidata após decisão técnica** (não criar módulos sem evidência):
```text
tsp-core/                 lógica TSP (inalterada)
tsp-app/                  aplicação e alternativa 2D existentes
tsp-visual-3d/            opcional: renderizador Java isolado, se spike justificar
assets/                   modelos/texturas com licenças verificadas
docs/DIRECAO_VISUAL.md
docs/ADR/0001-renderizador-3d.md
```

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

## 11. Questões abertas (resolver na spike)
1. JavaFX 3D estilizado ou jMonkeyEngine para o renderizador principal?
2. HUD e renderização na mesma janela, ou módulos/janelas distintos?
3. Como acomodar visualmente refúgios com altitude lógica arbitrária sem falsear coordenadas?
4. Quais texturas/modelos entregam o melhor resultado visual pelo menor custo de manutenção?
5. Qual nível de detalhes mantém FPS e estabilidade suficientes no computador de demonstração?

**Decisão final de engine:** não tomada nesta versão. Avaliar na issue #23 antes de atualizar dependências gráficas.
