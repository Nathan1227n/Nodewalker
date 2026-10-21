# Validação do MVP — issue #13

**Coleta: 21/10/2026, America/Fortaleza.** Entrega originalmente planejada para **19/10/2026**. Essa data é planejamento histórico, não data de homologação realizada. O resultado desta coleta é **PASSOU na homologação automatizável**, com **aceite humano e Windows gráfico PENDENTES**. Não se declara homologação integral nem encerramento das issues #13/#17.

Produto de referência: main `9c23bb6bc914b6be563a16e1ad0b2ae1cfe0d80c`, merge da PR #37. Branch de validação: `chore/issue-13-mvp-validation`. O código de produção não foi modificado nesta entrega. O checkout gráfico isolado usa `bbf56d2` (mesmo produto, utilitários de validação e proteção do roteiro de transições contra entrada externa). O SHA final do relatório/PR e seus checks são informados na própria PR; registrar os checks depois do commit evita atribuir uma execução ao SHA anterior.

## Ambiente e métodos

Fedora 44 Workstation, Wayland, kernel 7.2.11-200.fc44.x86_64, Temurin **21.0.12.1**, Maven **3.9.11**, JavaFX **21.0.12**. Ryzen 7 5700X, 8 núcleos/16 threads, RAM reportada 31 GiB; NVIDIA RTX 5060, aproximadamente 8 GiB de VRAM, driver 615.78.08. [Dados brutos](evidencias/issue-13/environment.json).

- **A — headless:** JUnit/Surefire/Failsafe, CLI, auditorias matemáticas e benchmark. Sem janela.
- **B — janela real automatizada:** JavaFX em desktop Fedora, controles por API/eventos sintetizados, snapshots após pulsos e inspeção da árvore/Canvas. Roteiros com barreiras são identificados. Não equivalem a entrada humana.
- **D — debugger:** observação de thread/stack da composição de produção. Não é benchmark nem medição de responsividade.
- **C — humano físico:** operador usando mouse/teclado. **PENDENTE**, sem confirmação recebida nesta coleta. Robot também é automação, não método C.

## Matriz de aceite

A coluna Resultado refere-se ao método e ambiente indicados, sem extrapolação para outra plataforma. Os logs de cada roteiro, manifestos e comandos estão no [índice de evidências](evidencias/issue-13/README.md).

| ID | Requisito | Método / evidência | Resultado | Limitação / correção |
| --- | --- | --- | --- | --- |
| A01 | Main mesclada/sincronizada e Git limpo antes da branch | Git, SHA 9c23bb6 e merge PR #37 | PASSOU | Alterações seguintes são da branch de validação |
| A02 | Nova suíte da main | A: [baseline](evidencias/issue-13/main-verify.txt), [comando](evidencias/issue-13/main-verify.json) | PASSOU | 2373 testes; 0 falhas/erros/ignorados; ~14 s externos |
| A03 | CI Ubuntu e Windows da main | A: [run 39538305522](https://github.com/Nathan1227n/Nodewalker/actions/runs/39538305522) | PASSOU | Headless; não homologa janelas Windows |
| B01 | Clone remoto independente, Maven vazio e settings isolados | A: [comandos](evidencias/issue-13/clone/commands.json), [settings](evidencias/issue-13/clone/empty-settings.xml) | PASSOU | Nenhum target/classe/configuração de IDE copiado |
| B02 | `clean verify` e `clean install` no clone | A: [verify](evidencias/issue-13/clone/03-verify.txt), [install](evidencias/issue-13/clone/04-install.txt) | PASSOU | 2373 testes em cada comando; exit 0 |
| B03 | CLI do README | A: [saída](evidencias/issue-13/clone/05-cli.txt) | PASSOU | N4/657, custo 2799, ciclo 0–2–1–3–0; CLI oferece somente Força Bruta |
| B04 | `javafx:run`: gerar, buscar, mostrar resposta e alternar | B: [log](evidencias/issue-13/clone/08-javafx-default.txt), [verificações](evidencias/issue-13/clone/08-javafx-default/PASS.txt) | PASSOU | N5/23, BF, custo 2875; entrada por API de teste |
| B05 | Início explícito 2D e fechamento normal | B: [log](evidencias/issue-13/clone/09-javafx-2d.txt), [verificações](evidencias/issue-13/clone/09-javafx-2d/PASS.txt) | PASSOU | Fechamento por WINDOW_CLOSE_REQUEST; Maven exit 0 nas duas entradas |
| C01 | Ciclo, unicidade, retorno, custos, canonicidade | A: suíte core, Held–Karp, [auditoria](AUDITORIA_ALGORITMOS.md) | PASSOU | Oráculos limitados aos N pequenos documentados |
| C02 | Exemplo do professor: seis ciclos BF, ótimo 11, rota canônica | A: AcademicTspExampleTest e [saída real](evidencias/issue-13/academic-reference.txt) | PASSOU | 1+3+3+4=11; BT avaliou 4 ciclos e podou o restante |
| C03 | Dijkstra: fila mínima, relaxamentos, predecessores e Floyd–Warshall | A: suíte core, [contratos](CAMINHOS_MINIMOS.md) | PASSOU | Problema de caminhos mínimos; não resolve TSP |
| C04 | Zero, MAX, BigInteger, desconexões e estados parciais | A: suíte core/app, [referências](evidencias/issue-13/academic-reference.txt) | PASSOU | Cadeia manual: 2×MAX=18446744073709551614; sem editor manual na UI |
| C05 | Solvers e preparação fora da FX Application Thread | D: [stacks e avaliações](evidencias/issue-13/threads.json); revisão de OperationalController | PASSOU | Três solvers e geração em nodewalker-compute; mundo em nodewalker-presentation; FX=false |
| D01 | N3/5/8/12, presets, mesma instância e resultados reais | B: [operacional 3D](evidencias/issue-13/graphics/operational-3d/review.txt), [manifesto](evidencias/issue-13/graphics/operational-3d/operational-manifest.tsv) | PASSOU | N12 fatorial sempre limitado/cancelável |
| D02 | Origem/topologia; destino sem recalcular | A+B: controlador, operacional e pesos | PASSOU | Completo/esparso na UI; desconectado pela API/testes |
| D03 | Cancelar geração/preparação/busca; preservar dados válidos | A+B: [transições](evidencias/issue-13/graphics/transitions-3d/review.txt), testes de barreira e controle | PASSOU | Sem sleeps como fundamento; cancelamento manual é outra evidência |
| D04 | Tempo/nós, reexecução e callbacks antigos | A+B: controles core, controlador e [transições 2D](evidencias/issue-13/graphics/transitions-2d/review.txt) | PASSOU | K−1/K/K+1 e relógios injetados; não exige esperar limite real de relógio em teste |
| D05 | Comparação e janela de resultados | B: [resultados 3D](evidencias/issue-13/graphics/results-3d/review.txt), [2D](evidencias/issue-13/graphics/results-2d/review.txt) | PASSOU | Histórico limitado a oito; tempos de uma execução não são benchmark |
| E01 | Câmera, órbita, zoom, seleção, foco, decoração e simplificação | B: [mundo 3D](evidencias/issue-13/graphics/world-3d/review.txt) e operacional | PASSOU | Eventos/API automatizados; conforto humano PENDENTE |
| E02 | Pesos, colisões, projeção, tabela integral e acumulado | A+B: [pesos 3D](evidencias/issue-13/graphics/weights-3d/review.txt), [2D](evidencias/issue-13/graphics/weights-2d/review.txt) | PASSOU | Filtro gráfico; sem teste de oclusão do terreno; todos os termos na tabela |
| E03 | Pausar/repetir, alternar 3D/2D e resize | B: [reprodução](evidencias/issue-13/graphics/playback-3d/review.txt), pesos e operacional | PASSOU | Apresentação de resultado pronto, sem trilha interna |
| E04 | Fechar durante busca | B: roteiro operacional real; A: testes do controlador | PASSOU | Sem espera da UI pelo encerramento do executor |
| E05 | Mouse e teclado físicos no Fedora | C: [checklist abaixo](#checklist-humano-fedora) | PENDENTE | Requer execução/confirmação explícita do responsável |
| F01 | `--2d` solicitado pelo usuário | B: clone, operacional e pesos em software | PASSOU | Escolha explícita; não prova falta de Scene3D |
| F02 | Fallback automático com Scene3D indisponível no pipeline software | B: [mundo fallback](evidencias/issue-13/graphics/world-fallback/review.txt), operacional/pesos fallback | PASSOU | Feature SCENE3D=false verificada; condição induzida por `-Dprism.order=sw`, sem mock booleano |
| F03 | Desktop Windows real 3D/2D | C: [roteiro Windows abaixo](#roteiro-windows--issue-17) | PENDENTE | CI Windows não abre janela; issue #17 preservada |
| F04 | Hardware/driver nativamente sem Scene3D | B/C: ambiente adicional necessário | PENDENTE | Pipeline software não homologa outra máquina/driver |
| F05 | Fallback após exceção real de criação do renderer 3D | Inspeção do tratamento; falha não observada nesta coleta | PENDENTE | Não se injetou defeito no produto para fabricar evidência |
| G01 | Benchmark controlado e reproduzível | A: [metodologia e dados](benchmarks/issue-13/README.md) | PASSOU | 252 chamadas; 36 baterias; 2 aquecimentos+5 medidas; não generaliza desempenho |
| H01 | Procedência e carregamento local dos assets | Inspeção, [hashes](evidencias/issue-13/asset-hashes.json), [origem](TEXTURAS.md) | PASSOU | Dois PNGs de geração por IA; nenhum asset externo importado ou download em runtime |
| H02 | Documentação atual e roteiro de 5–8 minutos | [Roteiro acadêmico](DEMONSTRACAO_ACADEMICA.md), README/roadmap atualizados | PASSOU | Registros históricos mantidos; DOCX não precisaram de alteração |
| I01 | Modo Aula interno e grafo dirigido | Escopo explicitamente excluído | NÃO APLICÁVEL | Não implementados nesta PR |
| J00 | `clean verify` final sem display | A: [saída final](evidencias/issue-13/final-verify.txt), [comando/ambiente](evidencias/issue-13/final-verify.json) | PASSOU | 2373 testes; 0 falhas/erros/ignorados; 13,983 s externos; produto inalterado |
| J01 | CI e diff do SHA final da PR | Apuração final nos checks/descrição da PR | PENDENTE nesta coleta local | Deve passar antes da entrega; resultado anexado na PR, sem confundir SHA anterior |

## Clone limpo: método e resultados

O clone remoto foi feito em `/tmp/nodewalker-issue13-qbtczis8/repo`, checkout destacado do SHA 9c23bb6. O diretório `/tmp/nodewalker-issue13-qbtczis8/m2` foi criado vazio. Os parâmetros `-s`, `-gs` e `-Dmaven.repo.local` isolam settings e artefatos; não se copiou configuração da IDE nem NodeWalker instalado no Maven principal. JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/MAVEN_OPTS herdados foram removidos pelo driver. O workspace principal não foi limpo para simular instalação nova.

| Operação | Duração externa | Exit code |
| --- | --- | --- |
| Clone remoto | 39,576 s | 0 |
| `mvn … clean verify` | 22,351 s | 0 |
| `mvn … clean install` | 14,939 s | 0 |
| CLI N4/657, 3 aquecimentos/5 medidas | 0,114 s | 0 |
| `mvn … -f tsp-app/pom.xml javafx:run` | 7,927 s | 0 |
| Mesmo comando com `-Djavafx.args=--2d` | 3,319 s | 0 |
| Git status após build e GUI | Limpo | 0 |

O processo GUI usa a **main class original**, sem substituição de Application ou solver. Um javaagent de teste sem transformação de bytecode, compilado contra as classes/dependências do próprio clone, apenas aciona controles por API e observa a cena. JAVA_TOOL_OPTIONS aponta esse driver exclusivamente para o ensaio; ele ignora a JVM Maven. Os fontes estão em `tools/validation/CloneSmokeAgent.java` e `CloneSmokeDriver.java`. Fecha por WINDOW_CLOSE_REQUEST, sem System.exit no caminho aprovado. O driver tem abort de segurança somente em falha, que produziria exit diferente de zero e FAIL.txt. Não faz parte dos JARs do produto.

A instância N5/23 concluiu BF com ciclo 0–1–4–2–3–0 e custo 2875; a tabela contém cinco termos e o retorno. A mesma cena/resultado foi preservada no 2D. Os logs não são apenas prova de processo aberto: há screenshots, verificações dos valores e exit normal. Os seis PNGs do clone não foram editados.

Reprodução, em diretório temporário NOVO, da raiz desta branch/PR (substituir apenas o caminho temporário):

```bash
python3 tools/validate_clean_clone.py --root /tmp/nodewalker-auditoria-nova --sha 9c23bb6bc914b6be563a16e1ad0b2ae1cfe0d80c --phase build
python3 tools/validate_clean_clone.py --root /tmp/nodewalker-auditoria-nova --sha 9c23bb6bc914b6be563a16e1ad0b2ae1cfe0d80c --phase gui
```

O segundo comando exige desktop Linux e executa os alvos do README com um driver de teste adicional. A execução normal do usuário continua sendo `mvn clean install` e `mvn -f tsp-app/pom.xml javafx:run`, sem agente.

## Auditoria e valores atuais

[Saída real reproduzível](evidencias/issue-13/academic-reference.txt):

- Slides TSP: ambos concluem com **11**, ciclo **0–1–3–2–0**, soma **1+3+3+4**; BF avalia **6** ciclos. Não se exige que BT avalie seis, pois ele poda.
- N5/23: ambos concluem com **2875**, ciclo **0–1–4–2–3–0**, soma **487+212+662+997+517**.
- Dijkstra N8/657, topologia esparsa seed23/extras0, origem0/destino5: **3463**, caminho **0–2–3–7–5**, soma **877+936+764+886**. Destino2: **877**, caminho **0–2**, reutilizando a busca.
- Cadeia manual: distância **18446744073709551614**, vértice isolado INALCANCAVEL. Com limite de duas fixações, distância MAX até1 confirmada; até2 ausente e NAO_RESOLVIDO.
- N5/23 BF com um nó: LIMITE_ATINGIDO, incumbente **3503**, ciclo 0–1–2–3–4–0, sem certificado.

A revisão confirma enumeração lexicográfica, retorno, poda parcial não negativa, desempate canônico, presença explícita de incumbentes, somas long exatas no TSP e BigInteger no Dijkstra. Overflow TSP continua ERRO conservador, inclusive quando outra rota representável poderia existir; não foi mascarado como solução. Interrupção não certifica ótimo. Predecessores Dijkstra seguem ordem de fixação estritamente anterior, mesmo com zero. INALCANCAVEL depende de conclusão.

Held–Karp e Floyd–Warshall continuam exclusivamente em testes. O índice de classes/contagens está em [test-summary.json](evidencias/issue-13/test-summary.json). Os JARs core e CLI foram inspecionados e não contêm esses oráculos. Não foi encontrada necessidade de modificar os solvers. O exercício dirigido informado do professor (0–2–3, custo9) não foi executado como se fosse não direcionado; continua fora do contrato atual.

## Incidentes e prioridades

| Classe | Ocorrência | Tratamento / situação |
| --- | --- | --- |
| P0 produto | Nenhum defeito bloqueante reproduzido | Nenhum aberto nesta coleta |
| P1 do procedimento | Primeiro roteiro em target compartilhado encontrou PathSegment ausente no classpath | [Falha preservada](evidencias/issue-13/incidents/initial-classpath-failure.txt). Causa externa exata não estabelecida; checkout gráfico isolado/recompilado passou. Nenhuma correção de solver |
| P1 do procedimento | Teste de câmera recebeu mudança de zoom não comandada pelo roteiro | [Valores do debugger](evidencias/issue-13/incidents/camera-debug.txt). Proteção contra InputEvent externo apenas no teste de transições; 3D/2D passaram. O roteiro de câmera continua exercitando eventos separadamente |
| P2 / limitação conhecida | Custos em overlay 3D sem teste de oclusão do terreno; densidade/colisões ocultam etiquetas | Legenda informa limitação; tabela integral preservada. Sem polimento novo |
| Pendência de ambiente | Robot não moveu a câmera no Wayland disponível | [Log](evidencias/issue-13/graphics/robot-attempt/review.txt), exit1. Automação nativa inconclusiva; não demonstra defeito da câmera, cujos eventos/API passaram. Método C permanece PENDENTE |
| Pendência externa | Windows desktop e interação humana | Não são defeitos demonstrados do produto; exigem responsável/ambiente. Não se declara #17 concluída |

**Não há P1 de produto confirmado e aberto exigindo aprovação de workaround nesta coleta.** Caso a validação humana revele P0/P1, reproduzir, classificar e repetir os testes afetados antes do aceite. Não incluir P2 opcional nesta PR.

## Checklist humano Fedora

**Estado: PENDENTE até confirmação explícita.** Em checkout da branch da PR, JDK21/Maven3.9+, execute:

```bash
git rev-parse HEAD
mvn clean install
mvn -f tsp-app/pom.xml javafx:run
```

- [ ] Gerar **N5/seed23**; executar BF e BT na mesma instância. Ambos CONCLUIDO, custo **2875**, ciclo **0–1–4–2–3–0**; comparação concordante.
- [ ] Arrastar no mapa, usar roda, selecionar/focar refúgio e vista superior; alternar 3D/2D. Ocultar decoração e ativar simplificação. Confirmar conforto e legibilidade.
- [ ] Ativar custos da solução e das conexões. Abrir Detalhes/Mapa, conferir fundo escuro e legenda compacta. Em resultados, conferir cinco termos e **487+212+662+997+517=2875**, incluindo retorno. Pausar/repetir/Mostrar tudo sem iniciar outra busca.
- [ ] Gerar **N8/seed657**. Dijkstra esparso, seed da topologia23, extras0, origem0/destino5: **3463**, caminho **0–2–3–7–5**. Trocar destino para2: **877**, sem novo runId. Testar completo e nova origem; só destino reutiliza resultado.
- [ ] Gerar N5/23 novamente, BF, Ajustes: **nós1/tempo2000ms**. Reexecutar: LIMITE_ATINGIDO, custo **3503**, sem ótimo certificado. Restaurar nós1000000. Redimensionar e abrir/fechar a janela de resultados.
- [ ] Para cancelamento/fechamento, N12/23 BF, **nós5000000/tempo5000ms**, confirmar busca limitada. Cancelar enquanto EXECUTANDO; verificar CANCELADO e eventual incumbente sem certificado. Reexecutar e fechar a janela durante o trabalho. Se concluir/atingir limite antes do clique, registrar o estado real; isso não comprova cancelamento físico.

Geração pode terminar antes de um clique humano. Se conseguir cancelar enquanto a etapa ainda está ativa, registrar isso separadamente, sem confundir com solver cancelado. Cancelamento determinístico da geração já tem evidência A/B; não fabricar uma confirmação C quando a geração terminou primeiro.

Retorno esperado do responsável: **SHA, data, Fedora/Wayland ou X11, itens aprovados/reprovados, comportamento observado e pendências**. Sem esse retorno, as caixas ficam abertas.

## Roteiro Windows — issue #17

**Estado: PENDENTE. Nenhum desktop Windows foi utilizado.** Em PowerShell com Git, JDK21 e Maven3.9+:

```powershell
git clone https://github.com/Nathan1227n/Nodewalker.git Nodewalker-validacao
cd Nodewalker-validacao
git switch chore/issue-13-mvp-validation
git rev-parse HEAD
java -version
mvn -version
mvn clean verify
mvn clean install
mvn -f tsp-app/pom.xml javafx:run
```

Executar os quatro primeiros itens do checklist Fedora; registrar se 3D inicia, câmera/seleção funcionam e 2D mantém o resultado. Fechar normalmente. Depois testar a entrada explícita:

```powershell
mvn -f tsp-app/pom.xml javafx:run '-Djavafx.args=--2d'
```

Para um ensaio adicional de pipeline software, que deve ser identificado como tal:

```powershell
$nodewalkerJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = "$nodewalkerJavaOptions -Dprism.order=sw".Trim()
    mvn -f tsp-app/pom.xml javafx:run
} finally {
    if ($null -eq $nodewalkerJavaOptions) {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_TOOL_OPTIONS = $nodewalkerJavaOptions
    }
}
```

O trecho preserva e restaura JAVA_TOOL_OPTIONS. Registrar suporte Scene3D, modo inicial, botão3D desabilitado quando indisponível, screenshot, console, GPU/driver e exit code. Software não equivale a testar um computador fisicamente sem 3D. Uma falha real deve ser descrita com console e comportamento do fallback; não presumir que ocorreu.

## Fecho da entrega automatizada

Quatorze roteiros obrigatórios de janela real passaram no checkout isolado; a tentativa Robot separada não passou e permanece registrada. A suíte do produto conserva 2373 casos; nenhuma funcionalidade nova, dependência, algoritmo ou refatoração visual foi introduzida. O benchmark e o ensaio debug têm escopos distintos; não se mediu FPS apresentado ou memória estabilizada.

O `mvn clean verify` final foi repetido sem DISPLAY/WAYLAND_DISPLAY: exit0, 2373 testes, zero falhas/erros/ignorados, 13,983 s externos. [Comando e saída](evidencias/issue-13/final-verify.json). `git diff --check` e CI Ubuntu/Windows do SHA final são conferidos na publicação; resultado anexado à PR para completar J01, sem atribuir checks ao commit errado. Sem merge ou encerramento automático de #13/#17. A [demonstração acadêmica](DEMONSTRACAO_ACADEMICA.md) usa valores obtidos nesta coleta e distingue apresentação da resposta de rastreamento interno.
