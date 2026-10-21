# Pesos e decomposição de custos

Revisão de escopo acadêmico de **20/10/2026**, posterior ao merge `627de81f` da PR #36. Esta entrega acrescenta apresentação auditável dos resultados reais, sem instrumentar a busca. Força Bruta e Backtracking continuam resolvendo o **TSP**; Dijkstra clássico resolve **caminhos mínimos**. Um caminho de Dijkstra não é um ciclo do caixeiro-viajante.

## Operação

Em **Detalhes e ajustes → Mapa**, escolher:

- **Sem custos** (padrão): mantém o mapa anterior.
- **Custos da solução**: pesos das arestas do resultado selecionado.
- **Custos das conexões**: inclui as conexões secundárias disponíveis, com escopo **Disponíveis (filtradas)** ou **Incidentes à seleção**. É possível ocultar as conexões secundárias novamente.

As etiquetas usam inteiros completos da matriz original. Ciano indica solução certificada/confirmada; âmbar indica incumbente TSP não certificada. Conexões secundárias permanecem discretas e não fazem parte da solução.

**Apresentar resultado → Resultado** contém a tabela completa: número do segmento, par de cidades, peso da matriz e acumulado exato. O último segmento TSP é identificado como retorno. A expressão abaixo da tabela pode ser selecionada; **Copiar resumo** inclui todos os termos. A tabela tem rolagem própria para ciclos maiores. Informações longas nas células têm tooltip, sem truncamento numérico do modelo ou do texto copiado.

## Fonte matemática

`CostBreakdown` é imutável e construído durante a preparação do modelo, em worker:

| Problema | Fonte | Conferência |
| --- | --- | --- |
| TSP | `TspResult.route()` e `TspProblem.cost(from,to)` | N termos, incluindo retorno; soma igual a `TspResult.cost()` |
| Dijkstra | `ShortestPathResult.pathTo(destination)` e `RefugeGraph.cost(from,to)` | Arestas existentes; soma `BigInteger` igual a `distanceTo(destination)` |

A decomposição nunca lê o subconjunto gráfico. Todos os acumulados usam `BigInteger`, inclusive no TSP; isso não amplia o domínio `long` do solver TSP. Zero e `Long.MAX_VALUE` são valores legítimos. Dijkstra conserva distâncias maiores que `long`. Conferir uma soma demonstra consistência, não executa nova prova de ótimo.

TSP interrompido com incumbente mostra o ciclo completo e **incumbente não certificada**. Sem incumbente, não existe soma numérica de solução. Dijkstra interrompido pode mostrar caminho/distância de um destino **confirmado**; destino não resolvido não recebe soma confirmada. Inalcançável só vem do resultado concluído. Origem igual ao destino confirmado tem caminho unitário, nenhuma aresta e soma zero. Nenhum retorno artificial é inserido.

A tabela usa o resultado ativo da instância. Troca de configuração, destino ou execução coloca a apresentação pendente sem reutilizar etiquetas/somas antigas; os controles `epoch/revision` existentes descartam publicações obsoletas. A mesma `PreparedWorld`, terreno, câmera e seleção são preservados ao trocar resultados da mesma instância.

## Filtragem visual

A legenda distingue três conjuntos:

1. **Grafo:** quantidade matemática de arestas não direcionadas. TSP tem N(N−1)/2; Dijkstra usa a topologia efetiva.
2. **Na camada / candidatas a peso:** ligações selecionadas para renderização e seu subconjunto elegível para pesos, antes de câmera/reprodução/colisões. Durante a reprodução, nem todas as ligações dessa camada já estão desenhadas.
3. **Etiquetas:** valores efetivamente pintados naquele frame.

Essa seleção não remove arestas do solver nem altera custos, XYZ, topologia ou matriz. TSP mantém o filtro incidente existente, até 32 conexões. Dijkstra esparso usa até 256 conexões secundárias; no completo, usa uma união limitada de incidentes à seleção, origem e destino. No escopo incidente, apenas a seleção define as secundárias. As arestas da solução continuam prioritárias em ambos os escopos.

A política é compartilhada entre 3D e 2D. Prioridade: identificação dos refúgios, custos da solução, custos secundários. Colisões com os rótulos dos refúgios e outras etiquetas são evitadas com margens e deslocamentos curtos; se não houver espaço, a etiqueta é omitida. A tabela continua contendo todos os termos. O teto é 48 etiquetas, reduzido pela área disponível (uma por aproximadamente 15.000 px²); conexões secundárias muito curtas na tela (<44 px) não recebem etiqueta. O N não é limitado matematicamente por essa política.

## Projeção e recursos

Em 3D, um **Canvas transparente** fica sobre a SubScene e sob os rótulos dos refúgios. A projeção do ponto médio visual usa a mesma pose que transforma a câmera efetiva, campo de visão vertical, viewport e planos near/far. Órbita, zoom e redimensionamento invalidam o layout. Etiquetas atrás da câmera, além do clipping ou sem espaço para o texto inteiro são omitidas.

**Limitação explícita:** esse overlay não usa buffer de profundidade. Um ponto no viewport e à frente da câmera pode estar encoberto pelo terreno ou por um refúgio 3D. A legenda informa que a oclusão pelo terreno não é testada. Não se declara visibilidade física da conexão apenas por haver projeção válida.

Em 2D, outro Canvas usa a mesma seleção/layout e a projeção XZ efetiva. Y lógico continua disponível na inspeção. Não se instanciam Labels por aresta; existe um Canvas por renderer e um único medidor de texto reutilizado. O cache de medidas é limpo ao trocar o modelo.

Decomposição: O(N) no TSP ou O(comprimento do caminho) no Dijkstra, preparada uma vez por apresentação. Seleção limitada de secundárias: O(N+C) nos casos incidentes, sem enumerar O(N²) no grafo completo. Em mudança de câmera/layout, os candidatos são ordenados por prioridade e testados contra obstáculos; há no máximo N termos da solução + 256 secundárias e 48 etiquetas aprovadas. Os rótulos de refúgios mantêm os limites existentes. Revisões e chaves de projeção evitam recalcular layout e medidas em frames idênticos. As somas não são recalculadas por frame.

A matriz O(N²), topologia e recursos do solver continuam sob os preflights existentes. Não há promessa de FPS ou limite real de heap derivado dos limites do overlay. Não foi realizado benchmark de desempenho nesta entrega.

## Reprodução

O rodapé identifica **“Reprodução do resultado calculado”**. O acumulado avança somente depois de um segmento inteiro: uma aresta parcialmente desenhada não contribui com fração do seu peso. Pausar conserva o acumulado; repetir volta a zero; alternar 2D/3D preserva a reprodução e o resultado. Essas ações não executam o solver. Etiquetas podem aparecer quando metade do segmento estiver desenhada para favorecer legibilidade, sem antecipar o acumulado.

## Referências e validação

- [Auditoria matemática e exemplo do professor](AUDITORIA_ALGORITMOS.md).
- [Proposta separada: grafos dirigidos e rastreamento interno](PROPOSTA_GRAFOS_DIRECIONADOS.md).
- [Capturas reais, manifestos e limites da homologação](evidencias/pesos-didaticos/README.md).

A suíte conserva Held–Karp e Floyd–Warshall independentes, sem promovê-los a produção. O roteiro novo `python3 tools/review_world.py --weights` abre janelas reais; `--software-2d` e `--software-auto` verificam Canvas e fallback. Esses roteiros não participam do CI headless. A issue #13 não é iniciada por esta entrega; rastreamento interno de permutações, podas e relaxamentos permanece fora do escopo.

## Revalidação após o merge da PR #37 — 21/10/2026

O parágrafo anterior registra o escopo original da camada. A #13 agora reexecutou os roteiros de pesos N=5/8/12 em janela real, inclusive filtros, custos originais, tabela integral, retorno TSP, acumulado por segmento completo, destino sem busca, colisões, legenda compacta e fundo escuro. [Capturas e manifestos atuais](evidencias/issue-13/README.md), [matriz de aceite](VALIDACAO_MVP.md) e [demonstração](DEMONSTRACAO_ACADEMICA.md). Mantém-se a limitação de oclusão do overlay 3D; não se declarou medição de FPS ou memória estabilizada. A navegação física humana e Windows desktop ainda exigem confirmação.
