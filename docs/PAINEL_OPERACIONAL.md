# Painel operacional — issue #12

**Atualização de homologação — 21/10/2026:** painel mesclado pela PR #36, com pesos/decomposição da PR #37. A [validação #13](VALIDACAO_MVP.md) reexecutou os fluxos reais N=3/5/8/12 em 3D, 2D explícito e fallback software: geração, três algoritmos, destino sem nova busca, comparação, cancelamento, resultados, reexecução e fechamento durante trabalho. [Roteiro pessoal de mouse/teclado](VALIDACAO_MVP.md#checklist-humano-fedora) permanece PENDENTE; automação não o substitui. [Demonstração reproduzível](DEMONSTRACAO_ACADEMICA.md).

Revisão de escopo/integração de 19/10/2026, após o merge da PR #35 (`8e1f0c7`).
O aplicativo opera dois problemas distintos. Força bruta e backtracking resolvem o TSP,
com ciclo hamiltoniano e retorno à origem 0. Dijkstra clássico resolve caminhos mínimos
no grafo de conexões disponíveis; seu caminho não é uma solução do TSP.
Os algoritmos e contratos matemáticos do `tsp-core` não foram alterados nesta integração.

## Operação

1. Abra a aplicação com os comandos do README. Ela inicia sem instância, com a indicação **Crie uma instância**.
2. Informe **Refúgios (N)** e **Seed** nos controles principais e clique **Gerar**. Alternativamente selecione
   um preset N=3/5/8/12 no seletor **Exemplos**. Presets geram a instância, sem executar busca automaticamente.
3. Escolha o problema/algoritmo e consulte **Detalhes e ajustes → Ajustes** para o preflight. Ajuste os limites e use
   **Aplicar ajustes**. Valores pendentes nos campos não regeneram a instância.
4. Clique **EXECUTAR**. Alterações de configuração ainda não aplicadas são validadas
   antes da busca. Fora da faixa TSP recomendada, confirme explicitamente a busca limitada.
5. **CANCELAR** solicita o token cooperativo e aguarda o resultado do núcleo.
   **EXECUTAR NOVAMENTE** cria outro identificador de execução sobre a mesma matriz.
6. O resultado principal fica sempre à vista após a busca; métricas ficam em **Detalhes e ajustes**; a comparação fica em **Comparar TSP**. O histórico mantém até oito execuções
   da instância atual. Geração substitui a instância e limpa seu histórico.

Ações incompatíveis ficam desabilitadas durante trabalho. Campos N/seed podem ser editados
como configuração pendente; a identidade/N/seed da instância ativa continuam visíveis.
Algoritmo, custo/distância, certificação e tempo **interno** aparecem no resultado.
Rotas/valores podem ser copiados sem truncamento; métricas ausentes aparecem como
**não informado**. Não há percentual de progresso nem tempo de geração disfarçado de busca.

O modo Dijkstra permite origem e destino, topologia **Completa (implícita)** ou
**Esparsa conectada**, seed e extras entre 0 e N−2. A geração `chain-chords-v1` do núcleo
é preservada. A topologia manual permanece na API/testes, sem editor neste painel.
Cada aresta existente usa exatamente `problem.cost(u,v)`. O terreno não fornece custos.

Trocar somente o destino reutiliza `ShortestPathResult`, sem executar Dijkstra novamente.
Origem, topologia ou instância diferentes invalidam a solução anterior. Estado do destino:

- **CONFIRMADO**: distância definitiva e caminho disponíveis, inclusive se a busca global foi interrompida.
- **INALCANCAVEL**: ausência comprovada por execução concluída.
- **NAO_RESOLVIDO**: sem distância mínima certificada; rótulos tentativos não são expostos.

Origem igual ao destino confirmado mostra somente a origem, distância zero e nenhuma
aresta artificial. Distâncias usam `BigInteger`; `Long.MAX_VALUE` continua um peso válido.
Empates conservam a política determinística do núcleo, sem promessa lexicográfica para Dijkstra.

A comparação TSP usa apenas execuções da mesma instância. Resultados concluídos dos dois
algoritmos devem concordar no custo e na primeira rota canônica. O histórico sinaliza
inconsistência se divergirem. Execuções interrompidas não recebem certificação de ótimo.
Dijkstra mantém resultados próprios, sem comparação de seu custo com o de um ciclo TSP.

## Concorrência e ciclo de vida

`OperationalController` e seus snapshots/serviços são Java puro, sem inicializar JavaFX.
O executor de cálculo tem uma thread e fila de uma tarefa. Geração, topologia e buscas
são serializadas; não há buscas concorrentes. Outro executor, também de uma thread/fila
limitada, prepara geometria/apresentação. Ambos têm descarte explícito e threads daemon.
Não há `CallerRunsPolicy` que possa transferir trabalho pesado para a thread JavaFX.

Cada geração recebe `instanceId`, cada busca recebe `runId`. Um epoch invalida configurações
substituídas; uma revisão adicional invalida apresentações antigas de destino. As entregas
são verificadas novamente **dentro** do callback despachado para a UI. Configuração pronta
só é publicada junto com a apresentação correspondente.

**Cancelar uma busca** não incrementa epoch, não cancela o Future do solver e não descarta
sua resposta. **Cancelar preparação** aciona o token de geração/preparação, incrementa epoch
e revisão e invalida callbacks anteriores, inclusive os já enfileirados, publicando CANCELADO.
**Substituir/fechar** também invalida as entregas. Geração/preparação têm sinal independente
dos tokens TSP/Dijkstra e não fabricam um resultado de solver. O status terminal devolvido
pelo núcleo é preservado, inclusive quando a conclusão precede um cancelamento tardio.

A UI não espera `awaitTermination`. O controlador publica mudanças de fase e resultados,
sem callbacks por estado explorado. Não há nova instrumentação por nó nos solvers.

Fases: SEM INSTÂNCIA → GERANDO INSTÂNCIA → VALIDANDO EXECUÇÃO → INSTÂNCIA PRONTA;
EXECUTAR → VALIDANDO EXECUÇÃO → EXECUTANDO ALGORITMO → terminal. CANCELAR acrescenta
CANCELAMENTO SOLICITADO enquanto aguarda a resposta. CANCELADO, LIMITE ATINGIDO e ERRO
são identificados com sua etapa/motivo. Essas fases não substituem `SolverStatus` ou
`ShortestPathStatus`; antes de uma resposta, o HUD informa SEM RESULTADO DE SOLVER.

## Mundo e apresentação

`PreparedWorld` compartilha transformação, coordenadas, refúgios, terreno, projeção e
buffers numéricos/UV entre `TspSceneModel` e `ShortestPathSceneModel`.
`WorldSceneModel` é somente uma fronteira geométrica dos renderizadores: não define custo,
ótimo ou um resultado matemático comum. Dijkstra não utiliza `TspRoute` nem `TspResult`.

TSP conserva `ResultPresentation`, ciclo fechado e `withResult`. Dijkstra destaca exatamente
os pares consecutivos de `pathTo(destination)`; a distância vem de `distanceTo(destination)`.
Um caminho confirmado tem estilo contínuo mesmo com status global CANCELADO/LIMITE,
sem usar a certificação TSP. Sem caminho confirmado, não há destaque anterior residual.

Atualizações da mesma instância conservam terreno, câmera, seleção e preferências. Mudança
3D/2D ou qualidade reinstala o adaptador usando geometria preparada, sem gerar outra matriz.
`modelProperty()` continua disponível para os consumidores TSP; `sceneModelProperty()`
acompanha qualquer apresentação. Rótulos priorizam seleção, BASE, origem [O] e destino [D].

Conexões secundárias podem ser ocultadas. O limite visual é 256: esparsas mostram todas
quando couberem; completas mostram um subconjunto determinístico incidente à origem/destino.
A legenda identifica o filtro e a quantidade na camada visual; o caminho destacado não
é removido pelo filtro. O solver continua considerando a topologia lógica inteira.

Geração, validação de resultados, terreno, normais e buffers/UV são preparados nos workers.
Na thread JavaFX ficam objetos gráficos e instalação: refúgios, decoração e arestas são
instalados em lotes com alvo de 2 ms por pulso. Uma tarefa individual pode ultrapassar esse
alvo; isso não é garantia de FPS. Fechamento para o instalador e descarta tarefas/nós.
Atalhos globais são ignorados quando controles de edição/seleção têm foco; clique no mapa
para voltar aos atalhos de exploração.

## Recursos e preflight

| Recurso | Política inicial |
| --- | --- |
| Geração | 100.000 candidatos; modelo de pico de 16 MiB |
| TSP | 1.000.000 nós / 2.000 ms; presets conservam seus orçamentos históricos |
| Dijkstra | 10.000 vértices / 1.000.000 entradas / 2.000 ms |
| Auxiliar Dijkstra | Estimativa de 64 MiB; topologia e matriz excluídas |
| Topologia explícita | Orçamento separado de 64 MiB |
| Mundo preparado | Estimativa de 16 MiB + 400.000×N bytes, teto de 64 MiB |
| Geometria/overlay | Estimativa de 60.000 + 1.200×N triângulos, teto de 160.000 |
| Histórico | Oito respostas da instância atual; sem duplicar mundo por execução |

Os limites de busca podem ser ajustados explicitamente; o painel sempre fornece limites.
N≤10 é recomendação operacional TSP do núcleo, não limite matemático. O app recusa a
**admissão visual** quando seu perfil não cabe, antes de alocar a matriz/mundo. Isso também
limita listas/objetos FX e não muda o domínio matemático das APIs do núcleo.

Estimativas não são heap reservado, medição de RSS ou garantia de ausência de OOM. Matriz
O(N²), cópias da geração, topologia, workspace, duas qualidades de mundo, JavaFX/driver e
snapshots têm custos distintos. O preflight Dijkstra mostra os três primeiros separadamente;
o preflight TSP mostra (N−1)!, identificando limite inferior quando o núcleo não calcula o
valor exato. Contagens Dijkstra e TSP não são métricas diretamente comparáveis.

## Validação e limites da evidência

`mvn clean verify` continua headless no CI Ubuntu/Windows. Testes do controlador usam
executores/filas controláveis e barreiras, sem sleeps. Testes de cancelamento durante busca
usam os pontos de observação package-private já existentes do núcleo, somente em `src/test`:
força bruta é pausada após incumbente real; Dijkstra, após duas fixações reais. O core de
produção não foi alterado. Testes de geometria/HUD incluem matrizes manuais, ausência,
inalcançabilidade, `Long.MAX_VALUE` e distância acima de `long`.

Roteiros gráficos opcionais, fora do CI:

```bash
python3 tools/review_world.py --operational --output tsp-app/target/review-panel
python3 tools/review_world.py --operational --software-2d --output tsp-app/target/review-panel-2d
python3 tools/review_world.py --operational --software-auto --output tsp-app/target/review-panel-fallback
python3 tools/review_world.py --output tsp-app/target/review-world-regression
```

O roteiro operacional usa a composição de produção, controles e algoritmos reais. O roteiro
histórico mantém fixtures de apresentação explicitamente identificadas. Nenhum deles equivale
a entrada humana, benchmark de FPS ou homologação completa da issue #13.
[Evidências da janela Fedora e pendência de entrada nativa](evidencias/issue-12/README.md).


## HUD simplificado e reprodução visual — 19/10/2026

Após revisão de usabilidade, o fluxo principal reúne N/seed, exemplos, algoritmo e executar,
sem a pilha de seções/caixas vazias. Origem/destino e topologia aparecem somente no Dijkstra.
O botão **EXECUTAR NOVAMENTE** reutiliza a instância; **Cancelar busca** aparece durante
o trabalho. Uma única área **Detalhes e ajustes** contém as abas Ajustes, Dados,
Mapa e Refúgio; a comparação passou à janela dedicada na revisão de 20/10. Os IDs de instância/execução continuam disponíveis em Ajustes.

Ao gerar uma instância, seus refúgios são revelados gradualmente em ordem de índice.
Ao receber o resultado, o mapa desenha cada ligação da sequência retornada e um marcador
percorre o trecho. **Pausar/Continuar**, **Repetir desenho**, **Mostrar tudo** e velocidade
0,25×…4× controlam somente essa apresentação. Conexões secundárias ficam desligadas por
padrão no TSP; entram ao escolher Dijkstra e podem ser ocultadas na aba Mapa.

**Esta é uma reprodução da resposta calculada**, não uma trilha da árvore de busca, das
fixações ou dos relaxamentos do solver. Instâncias pequenas realmente terminam em milissegundos.
O resultado conserva seu status verdadeiro durante toda a animação, com o tempo interno
indicado como **Cálculo**. Nenhum sleep ou atraso foi adicionado aos algoritmos. A formação
visual dos refúgios também não representa a ordem de visita de Dijkstra.

`WorldPlayback` é Java puro e recebe deltas de tempo nos testes. Um único AnimationTimer
da view avança a reprodução enquanto ativa; pausa, conclusão, substituição da instância e
fechamento interrompem o relógio correspondente. O desenho compartilha coordenadas, custo,
resultado, terreno e câmera. Os cilindros existentes crescem sem reconstruir terreno ou
criar um resultado TSP parcial. O Canvas 2D usa as mesmas frações de aresta.

A 1×, a revelação dura aproximadamente 1,5–3 s e a reprodução do caminho 2,4–10 s conforme
o tamanho. Esses valores são ritmo de apresentação, não estimativa de busca ou benchmark.
O avanço por frame é limitado para evitar saltos após uma pausa longa do desktop.

Roteiro específico de captura de estados intermediários reais:

```bash
python3 tools/review_world.py --playback --output tsp-app/target/review-playback
python3 tools/review_world.py --playback --software-auto --output tsp-app/target/review-playback-2d
```

O roteiro verifica pausa, cilindro parcial real na SubScene, continuidade 3D/2D, velocidade,
repetição sem nova busca e descarte da reprodução ao gerar outra instância.
[Capturas do HUD simplificado e da animação](evidencias/issue-12/simplificacao/README.md).

## Resultados para apresentação e comparação — 20/10/2026

Após uma execução, **Apresentar resultado** abre uma janela redimensionável com custo ou
distância em destaque, tempo interno, ciclo/caminho completo e conclusão em linguagem clara.
Métricas adicionais são recolhíveis. **Copiar resumo** inclui N, seed, identificação da
instância/execução, valores exatos e razão de interrupção quando presente. **Voltar ao mapa**
ou Esc fecha essa apresentação; câmera, geometria, seleção e animação permanecem no mapa.

**Comparar TSP** abre dois cartões lado a lado, Força Bruta e Backtracking. Cada cartão
mostra a tentativa mais recente do algoritmo retida no histórico da instância atual,
identificada pelo runId, com custo, certificação, tempo, métricas reais e ciclo.
Uma tentativa cancelada/limitada nova não é substituída silenciosamente por uma conclusão
antiga. O histórico continua limitado a oito respostas; a ausência de uma coluna significa
que não existe resultado desse algoritmo no histórico retido.

Os botões dos cartões executam/reexecutam o respectivo algoritmo pelo fluxo assíncrono
existente, sem regenerar cidades, usando os limites TSP atuais. Durante trabalho, novas execuções
ficam indisponíveis e **Cancelar busca** solicita o token cooperativo sem descartar a resposta. Fora da faixa recomendada, a execução deve ser confirmada no painel
principal: a janela de comparação não contorna a proteção para TSP grande.

A conclusão distingue espera por resultados, comparação parcial, concordância certificada
e divergência de custo/desempate. Somente duas conclusões certificadas podem produzir a
mensagem **Mesmo custo ótimo e mesmo ciclo**. Resultados de outra instância e resultados
Dijkstra não entram nas colunas. Custos não são classificados como ótimos por coincidirem.
Tempos são de uma única execução, não um benchmark nem uma classificação universal de
velocidade. A seção **Como ler esta comparação** explica os contadores diferentes.

Dijkstra tem apresentação própria: origem/destino, topologia, distância confirmada e caminho.
A janela acompanha mudanças de destino sem nova busca e remove resultados obsoletos ao
substituir/configurar a instância. Distâncias BigInteger e Long.MAX_VALUE são preservados;
um destino não resolvido/inalcançável não recebe distância numérica inventada.

Os modelos `ResultReport` e `TspComparison` são Java puro, testáveis sem JavaFX. A janela
opcional apenas lê snapshots; os solvers e o controlador de execução permanecem intactos.
O antigo texto de comparação deixa a área de ajustes, conservando a simplicidade do HUD.

Roteiros de validação com janela real, controles por API e algoritmos de produção:

```bash
python3 tools/review_world.py --results --output tsp-app/target/review-results
python3 tools/review_world.py --results --software-2d --output tsp-app/target/review-results-2d
```

[Capturas e resultados reais da comparação](evidencias/issue-12/resultados/README.md).


## Revisão da PR #36: transições e cancelamento — 20/10/2026

Ao trocar algoritmo/configuração, reexecutar ou trocar o destino, o controlador publica
imediatamente `PendingSceneModel`, sem segmentos, conexões antigas ou resultado matemático
embutido. Esse adaptador é O(1) e referencia o mesmo `PreparedWorld`; não prepara terreno,
valida rota nem reconstrói coordenadas. O resultado de solver anterior é removido na troca de
configuração/reexecução; na troca apenas de destino, a busca Dijkstra permanece em cache.

A publicação passa pelo mesmo `NodeWalkerApp.accept` dos outros estados. A view substitui
a camada de rota imediatamente, remove cursor/reprodução anteriores e mantém instância,
terreno, câmera e seleção. O mapa identifica **SEM RESPOSTA ATIVA** enquanto aguarda a
apresentação. Tarefas JavaFX de instalação antigas conservam suas revisões e não restauram
arestas. As preparações e validações custosas continuam nos workers. A transição neutra não
altera a preferência de conexões secundárias como se fosse uma nova escolha de problema.

A configuração só fica executável depois de sua apresentação aceita. A política de
cancelamento distingue explicitamente o dono do trabalho:

| Etapa cancelada | Política de estado e entrega |
| --- | --- |
| Geração | CANCELAMENTO SOLICITADO → CANCELADO; token cooperativo e invalidação de epoch/revisão. A instância ainda não admitida não é publicada. |
| Construção de topologia | Mesma transição explícita; descarta callbacks antigos, conserva mundo/instância já admitidos e remove grafo incompleto. |
| Preparação do mundo ou da configuração visual | Mesma transição; callbacks de prontidão já na UI não podem publicar INSTÂNCIA PRONTA depois do cancelamento. Conserva o mundo quando ele já existe; nenhuma resposta de solver é criada. |
| Busca TSP | Solicita o token TSP e mantém epoch/entrega. O núcleo devolve seu status real, incluindo ciclo incumbente válido quando encontrado. |
| Dijkstra | Solicita o token de caminhos mínimos e mantém epoch/entrega. Publica somente distâncias confirmadas devolvidas pelo núcleo. |
| Solver já terminou, apresentação/entrega ainda pendente | O cancelamento tardio não reescreve CONCLUIDO ou LIMITE_ATINGIDO já calculado. A resposta válida e seus dados são preservados. |

CANCELADO na preparação significa que a admissão/publicação foi encerrada; não significa
que a UI tenha esperado a thread terminar. Workers em andamento saem cooperativamente e
suas entregas ficam inválidas. **EXECUTAR** após preparação cancelada refaz apenas o que
falta, antes da busca; o runId só avança quando a busca realmente é iniciada. Cancelar essa
nova preparação também invalida a execução automática pendente. **Aplicar ajustes** pode
preparar novamente a mesma configuração, sem obrigar o usuário a mudar o algoritmo.

A apresentação de outro destino de uma busca já concluída usa apenas o cache e revisão
visual; não inicia solver nem altera seu status terminal. O histórico TSP é identificado
como histórico durante novas execuções. O ajuste opcional de retenção permanente por
algoritmo não foi incluído: permanece o histórico cronológico limitado a oito respostas.

A auditoria adiciona 30 casos headless, com executores manuais, callbacks retidos e barreiras
para geração/topologia/mundo em andamento. Inclui cancelamentos tardios, dados parciais,
reexecução, revisão de destino, retry cancelado e validação da identidade do mundo/grafo.
O roteiro `--transitions` verifica a árvore 3D real e o Canvas 2D, além dos snapshots:

```bash
python3 tools/review_world.py --transitions --output tsp-app/target/review-transitions
python3 tools/review_world.py --transitions --software-2d --output tsp-app/target/review-transitions-2d
```

[Política, manifestos e capturas das duas correções](evidencias/issue-12/transicoes/README.md).
O mapa também identifica cancelamento de geração sem manter a mensagem de geração em andamento.
Entrada humana com mouse/teclado continua pendente; essa automação não substitui a issue #13.
