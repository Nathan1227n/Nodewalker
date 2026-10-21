# Demonstração acadêmica — NodeWalker MVP

Roteiro de **aproximadamente sete minutos**, preparado em 21/10/2026 com resultados reais da [homologação automatizada](VALIDACAO_MVP.md). Não é uma declaração de homologação humana: o [checklist pessoal](VALIDACAO_MVP.md#checklist-humano-fedora) permanece pendente até confirmação do responsável.

## Preparação anterior à apresentação

JDK21 e Maven3.9+, desktop Fedora validado. Compile antes de começar a contar o tempo:

```bash
mvn clean install
mvn -f tsp-app/pom.xml javafx:run
```

Caso 3D não seja adequado à máquina, usar a entrada explícita `mvn -f tsp-app/pom.xml javafx:run -Djavafx.args="--2d"`. Isso é escolha do usuário; fallback automático por indisponibilidade é outro caso. Deixar uma cópia local das capturas e da tabela de valores abaixo. Tempos em milissegundos variam; não decorar uma duração como se fosse propriedade do algoritmo.

## 0:00–0:45 — Dois problemas distintos

Dizer: “No caixeiro-viajante, queremos um ciclo de custo mínimo que visite cada refúgio uma vez e volte à BASE 0. Força Bruta e Backtracking resolvem esse TSP. No caminho mínimo, queremos sair de uma origem e chegar a um destino pelas conexões disponíveis; usamos Dijkstra clássico. Ele não precisa visitar todos os refúgios nem retornar à origem.”

O terreno é apresentação. Cada conexão custa `TspProblem.cost(u,v)`, não a distância percorrida sobre montanhas. Dijkstra usa a mesma matriz, restringida pela topologia escolhida.

## 0:45–1:30 — Instância reproduzível

Informar **N=5**, **seed=23**, clicar **Gerar**. Aguardar os refúgios; mostrar BASE 0 e selecionar outro refúgio. A formação dos pontos é animação da instância já gerada, não ordem de visita de um solver.

Dizer: “N e seed reproduzem cidades e matriz. Vou usar esta mesma instância nos dois algoritmos, sem clicar Gerar novamente.” A identidade está em Detalhes/Ajustes.

## 1:30–2:45 — Força Bruta e Backtracking

Selecionar **TSP / Força Bruta** e executar. Esperado: **CONCLUIDO**, custo **2875**, ciclo **0→1→4→2→3→0**, ótimo certificado. Em N5 há (5−1)!=24 ciclos completos; inversos também entram na enumeração.

Selecionar **TSP / Backtracking**, executar na mesma instância e abrir **Comparar TSP**. Esperado: mesmo ótimo e mesmo ciclo. Mostrar as duas respostas certificadas. O tempo interno de cada execução não inclui geração nem a animação.

Dizer: “Força Bruta enumera todas as permutações. Backtracking elimina continuações que não podem melhorar o incumbente: os custos restantes são não negativos. Ele pode examinar menos ciclos, mas continua exato quando conclui. Os contadores de nós têm definições diferentes.”

Instâncias pequenas terminam rapidamente; não atrasar artificialmente a busca para parecer mais complexa.

## 2:45–3:45 — Custo auditável e retorno

Em **Detalhes e ajustes → Mapa**, escolher **Custos da solução**. Depois **Apresentar resultado → Resultado** e conferir:

| Passo | Conexão | Peso | Acumulado |
| --- | --- | --- | --- |
| 1 | 0→1 | 487 | 487 |
| 2 | 1→4 | 212 | 699 |
| 3 | 4→2 | 662 | 1361 |
| 4 | 2→3 | 997 | 2358 |
| 5 | 3→0 — retorno | 517 | 2875 |

**487+212+662+997+517=2875.** A tabela vem da matriz e do resultado completo, independentemente das etiquetas que couberam no mapa. Conferir soma não é uma nova prova do ótimo; essa garantia vem da conclusão da busca correta.

Voltar ao mapa. Mostrar Pausar/Repetir/Mostrar tudo brevemente: **“Reprodução do resultado calculado”**, não visualização interna de tentativas, permutações ou podas. O acumulado avança somente ao completar uma aresta.

## 3:45–5:15 — Dijkstra esparso

Gerar outra instância: **N=8**, **seed=657**. Selecionar **Caminhos mínimos / Dijkstra clássico**:

- Origem **0**, destino **5**.
- Topologia **Esparsa conectada**.
- Em Ajustes: seed das conexões **23**, extras **0**; Aplicar ajustes.
- Executar e escolher **Custos das conexões** no mapa.

Esperado: **CONCLUIDO**, destino **CONFIRMADO**, distância **3463**, caminho **0→2→3→7→5**. A topologia tem sete conexões não direcionadas. Abrir a decomposição:

| Conexão | Peso | Acumulado |
| --- | --- | --- |
| 0→2 | 877 | 877 |
| 2→3 | 936 | 1813 |
| 3→7 | 764 | 2577 |
| 7→5 | 886 | 3463 |

**877+936+764+886=3463.** Não existe retorno artificial ao 0. As conexões cinza são disponibilidade; o caminho destacado contém somente os segmentos do resultado.

Trocar destino para **2**, sem executar novamente: distância **877**, caminho **0→2**; runId permanece igual. Dizer: “Uma busca a partir da origem calculou distâncias para os vértices alcançáveis. Apenas reconstruí outro destino. Trocar a origem ou a topologia exigiria outra execução.”

Não comparar 3463 com 2875 como se fossem soluções concorrentes: são problemas e instâncias diferentes.

## 5:15–6:00 — Ler o mundo em 3D e 2D

Alternar **MAPA 2D / 3D**, selecionar um refúgio, usar vista superior/foco. Ocultar decoração se a cena ficar carregada. Geometria de câmera e terreno não altera matriz, resultado ou seleção.

Dizer: “O mapa filtra conexões/etiquetas para legibilidade; o solver usa a topologia inteira. A tabela conserva todos os termos. Em 3D, etiquetas são overlay sem teste de oclusão pelo terreno.” Não usar o comprimento visual como custo.

## 6:00–7:00 — Limites e respostas interrompidas

Para uma demonstração determinística e curta, gerar **N5/23**, selecionar BF, aplicar **limite de nós 1** e **tempo 2000 ms**, executar. Esperado:

- **LIMITE_ATINGIDO**.
- Ciclo incumbente **0→1→2→3→4→0**, custo **3503**.
- **ÓTIMO NÃO CERTIFICADO**, estilo âmbar.

Dizer: “Há um ciclo completo válido, mas a busca foi interrompida antes da prova global. Corresponder ao mínimo por acaso também não autorizaria certificação.” Restaurar nós 1000000 antes de nova busca normal.

O cancelamento por botão é cooperativo e preserva incumbente TSP ou distâncias já confirmadas de Dijkstra. Cancelar geração não produz resultado de solver. Se a execução terminar antes do clique, relatar conclusão natural; não inventar cancelamento.

Se houver até oito minutos, mostrar N12/23 com orçamento **5000000 nós / 5000ms**, confirmar busca limitada e clicar Cancelar enquanto estiver ativa. Não esperar uma busca fatorial ilimitada. Esse trecho é opcional e seu resultado/custo depende do instante do cancelamento.

## Referência literal dos slides TSP

O exemplo de quatro cidades foi reexecutado com os solvers reais e também está em teste automatizado. Para apresentar a matriz exata sem fingir que uma seed gera seus pesos manuais:

```bash
java --class-path tsp-core/target/classes tools/validation/AcademicReference.java
```

Matriz:

```text
0 1 4 6
1 0 2 3
4 2 0 3
6 3 3 0
```

Força Bruta: CONCLUIDO, **seis ciclos completos avaliados**, custo **11**, ciclo **0→1→3→2→0**, soma **1+3+3+4=11**. Backtracking: mesma solução/custo, quatro ciclos avaliados nessa execução e os demais podados. O painel não tem editor/importador de matrizes manuais; esse exemplo é demonstrado pelo teste/utilitário, não por um preset inventado.

Referência: “Aula 5 - Caxeiro Viajante.pdf”, página 4, dados fornecidos pelo responsável. Os arquivos originais não estão versionados nesta revisão. O exercício de “6-AlgDijkstra.pdf”, pp. 66–67, é **direcionado**, caminho informado 0→2→3/custo 9. O NodeWalker atual não o reproduz fielmente, pois usa topologia não direcionada e matriz simétrica. Grafos dirigidos continuam fora do MVP.

## Perguntas para a defesa

- **Por que a poda é correta?** Com pesos não negativos, completar o prefixo nunca reduz seu custo; se ele já é maior ou igual ao incumbente, não melhora a primeira ótima encontrada na ordem lexicográfica.
- **Como confirmar custos?** Somar todas as arestas da matriz, incluindo retorno TSP. Dijkstra soma somente seu caminho em BigInteger; não há infinito representado por MAX.
- **Como os algoritmos foram conferidos?** Testes diferenciais, Held–Karp independente para TSP pequeno e Floyd–Warshall independente para caminhos mínimos; referências somente em testes.
- **O que é certificado após interrupção?** TSP nunca certifica ótimo; Dijkstra conserva somente distâncias fixadas. Destino ainda não resolvido não recebe distância tentativa definitiva.
- **Qual a complexidade?** TSP: pior caso O(N·(N−1)!), auxiliar O(N). Dijkstra com heap indexado: O((V+E)log V) operações aritméticas, auxiliar O(V); matriz/topologia e bits dos inteiros têm custos próprios.
- **Quais limites ainda existem?** [Relatório](VALIDACAO_MVP.md): homologação humana e Windows gráfico pendentes; overlay sem profundidade; sem Modo Aula interno, grafo dirigido ou medição de FPS apresentado.
