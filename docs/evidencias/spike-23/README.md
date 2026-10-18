# Evidências da spike #23

Capturas reais e observações de 17/10/2026, obtidas pelos próprios protótipos em janelas abertas no Fedora/RTX 5060. Relatório e limites: [SPIKE_RENDERIZADORES.md](../../SPIKE_RENDERIZADORES.md).

- `javafx-reference.png` / `jme-reference.png`: mesmo enquadramento e composição normal.
- `javafx-simple.png` / `jme-simple.png`: malha reduzida e decoração desativada.
- `captures/`: metadados, eventos, comando do processo e log de cada captura. `samplingComplete=false` é esperado: essas execuções de três segundos não são benchmarks.
- `benchmark/`: três pares de execuções, com 10 s de aquecimento e 60 s de órbita automática. Contém intervalos, RSS, metadados e logs; não contém medições de apresentações da GPU.
- `benchmark-summary.md`: tabela reproduzida por `scripts/summarize.py` a partir dos arquivos de cada rodada.
- `environment.txt`: informações de ambiente coletadas antes dos ensaios. `xrandr` não estava instalado; a resolução nominal do monitor foi obtida por GLFW e consta nos metadados JME.
- `artifact-totals.json` e `*-artifacts.json`: tamanhos dos JARs locais Linux, incluindo dependências runtime; excluem a JVM e o cache Maven completo.
- `source-sha256.json`: hashes das fontes, testes, scripts e POMs entregues na spike.
- `validation.txt`: comandos e contagens da validação local.

Os PNGs foram apenas renomeados, sem edição dos pixels. Nos arquivos de texto, terminadores de linha foram normalizados para LF e espaços finais removidos; valores das amostras foram preservados.

Foco/visibilidade são amostrados por callback, incluindo aquecimento, e não representam proporções de tempo. Visibilidade do toolkit não prova ausência de oclusão por outra janela. O campo `unmodifiedBenchmark` indica ausência de comandos interativos na cena; não certifica foco ou condições externas constantes. Há pausas de aproximadamente um segundo e mudanças de foco no JME; a causa não foi isolada. Não usar esta série para afirmar superioridade de FPS ou eficiência de memória entre motores.

As capturas comprovam a cena renderizada nos dois motores; conforto da câmera, seleção, foco, fallback real e Windows gráfico continuam sujeitos à revisão humana. A ADR permanece provisória.
