# Roadmap / controle de gates

**Entrega planejada:** 19/10/2026. **Escopo do MVP oficial:** G0–G3. Não tratar G1 ou G2 isoladamente como MVP visual.

| Gate | Conteúdo | Critério de saída | Situação em 11/10 |
| --- | --- | --- | --- |
| G0 | Maven multimódulo, Java 21, JavaFX 21.0.12, JUnit 5.14.3, hello 3D/fallback | Compilação, testes e demonstração no Fedora/Windows | Compilação corrigida; 7 testes e CI Ubuntu/Windows aprovados; validações gráficas finais pendentes |
| G1 | Modelo, gerador, custos, força bruta e CLI de benchmark | Correção e determinismo, testes sem UI | A iniciar |
| G2 | Backtracking, poda, métricas, limites e cancelamento | Resultados iguais aos da força bruta e invariantes preservadas | Pendente |
| G3 | UI JavaFX 3D/2D, câmera e execução assíncrona | UI responsiva e rota auditável | Pendente |
| G4 | Modo Aula e recursos didáticos | Testes do MVP continuam passando | Posterior |
| G5 | Efeitos opcionais | Não interferem no TSP | Opcional |

## Plano diário (documento de consolidação de 11/10/2026)

- **11/10:** estabelecer G0 e abrir janela JavaFX 3D/fallback 2D.
- **12/10:** TspProblem, City, gerador, matriz de custos, validação e brute force.
- **13/10:** backtracking com poda e métricas.
- **14/10:** testes diferenciais, invariantes, cancelamento/limites, benchmark.
- **15/10:** mapa 3D, câmera e rota final destacada.
- **16/10:** interface assíncrona, HUD, reexecução, preflight.
- **17/10:** identidade visual; apenas melhorias seguras.
- **18/10:** revisão, documentação e teste em clone limpo.
- **19/10:** demonstração/entrega; sem novas funcionalidades de risco.

## G0: evidências e pendências — issue #1

Referência: [issue #1](https://github.com/Nathan1227n/Nodewalker/issues/1). As evidências anteriores abaixo foram informadas pelo desenvolvedor; as verificações desta correção foram executadas em 11/10/2026 (America/Fortaleza).

### Já validado pelo desenvolvedor antes desta correção

- [x] Java 21 e Maven disponíveis no Fedora.
- [x] Compilação original com `mvn clean verify` e 3 testes JUnit aprovados em `SearchSpaceTest`.
- [x] Janela JavaFX iniciando no Fedora, com visualização 3D no hardware disponível.
- [x] Botão “Alternativa 2D” exibindo o mapa bidimensional.
- [x] Código inicial publicado na `main` do GitHub.
- [x] Workflow anterior aprovado em Ubuntu e Windows.

### Verificado nesta correção

- [x] Alterações locais preservadas na branch `fix/g0-fallback-validation`, incluindo Actions v5, dependência JUnit, seletor e quatro testes.
- [x] Erro de compilação reproduzido e corrigido: faltava `{` após `private void setPreview(Node content, String mode)`. Os erros sobre classes sem nome/preview eram consequências da sintaxe; não foi habilitado `--enable-preview`.
- [x] Seleção inicial isolada em `PreviewModeSelector`, sem dependência de JavaFX: `supports3d && !force2d`.
- [x] Quatro combinações testadas com JUnit: suporte sem `--2d` → 3D; suporte com `--2d` → 2D; ausência de suporte com ou sem `--2d` → 2D.
- [x] Integração revisada no código: detecção por `Platform.isSupported(ConditionalFeature.SCENE3D)`, argumento `--2d`, textos de modalidade, alternância manual e botão 3D desabilitado sem suporte preservados. `DemoPreview` e os três testes de `SearchSpaceTest` não foram alterados; nenhum solver foi implementado.
- [x] `mvn clean verify`: pai, `tsp-core` e `tsp-app` com `SUCCESS`; 7 testes, 0 falhas, 0 erros e 0 ignorados.
- [x] `mvn clean install`: os três módulos com `SUCCESS`, os mesmos 7 testes aprovados e artefatos instalados no repositório Maven local.
- [x] `env -u DISPLAY -u WAYLAND_DISPLAY mvn --batch-mode --no-transfer-progress clean verify`: `BUILD SUCCESS`, com os mesmos 7 testes, demonstrando execução sem conexão com o desktop.
- [x] Workflow revisado: `actions/checkout@v5`, `actions/setup-java@v5`, Temurin 21, `cache: maven`, matriz Ubuntu/Windows e `mvn --batch-mode --no-transfer-progress clean verify` preservados.

Ambiente desta execução: Fedora Linux, Eclipse Temurin **21.0.12.1** e Apache Maven **3.9.11**. Os relatórios ficam em `tsp-core/target/surefire-reports/` (3 testes) e `tsp-app/target/surefire-reports/` (4 testes), gerados localmente e excluídos do Git.

`mvn -f tsp-app/pom.xml javafx:run` e `mvn -f tsp-app/pom.xml javafx:run -Djavafx.args=--2d` foram iniciados após a instalação e permaneceram em execução sem erro inicial no console. Ambos foram encerrados com Ctrl+C (código 130), não por falha observada de JavaFX. Isso, isoladamente, não comprova a aparência da janela nem a interação com os botões; a confirmação visual desta revisão permanece pendente.

O primeiro [CI de push](https://github.com/Nathan1227n/Nodewalker/actions/runs/38196497489), no commit `8f82fea`, passou em Ubuntu e Windows com 7 testes por sistema. A [execução separada da PR](https://github.com/Nathan1227n/Nodewalker/actions/runs/38196509084) passou no Windows, mas falhou no Ubuntu ao resolver `maven-clean-plugin:3.5.0` no Maven Central, antes de compilar. Os logs mostraram versões diferentes dos plugins implícitos entre os ambientes. Por isso, o POM pai agora fixa os plugins do ciclo de build nas versões já validadas localmente: clean 3.2.0, resources 3.3.1, jar 3.4.1 e install 3.1.2; compiler e surefire permanecem nas versões existentes. Após o ajuste (`426edd4`), `mvn clean verify` e `mvn clean install` passaram novamente no Fedora. O [CI de push](https://github.com/Nathan1227n/Nodewalker/actions/runs/38196725604) e o [CI da PR](https://github.com/Nathan1227n/Nodewalker/actions/runs/38196728956) passaram em Ubuntu e Windows; os logs da PR confirmam as versões fixadas e 7 testes por sistema, sem falhas, erros ou testes ignorados.

As Actions v5 usam Node.js 24; a documentação de [checkout](https://github.com/actions/checkout/tree/v5) e [setup-java](https://github.com/actions/setup-java/tree/v5) foi consultada. `setup-java@v5` requer runner 2.327.1 ou posterior e o cache Maven considera os POMs do repositório por padrão. O workflow usa runners hospedados `ubuntu-latest` e `windows-latest`; a compatibilidade foi confirmada nas execuções aprovadas acima (runner 2.337.0).

### Situação do aceite final

- [x] Nova execução do workflow com Actions v5 aprovada em Ubuntu e Windows, com links registrados acima.
- [x] [PR #16](https://github.com/Nathan1227n/Nodewalker/pull/16) publicada para `main` com `Refs #1`, sem encerrar a issue.
- [ ] Reconfirmar visualmente esta revisão no Fedora: início 3D quando disponível, troca para 2D, retorno ao 3D, textos e início com `--2d`.
- [ ] Validar renderização e legibilidade do fallback automático em ambiente real sem Scene3D, incluindo botão 3D desabilitado.
- [ ] Validar abertura e uso da janela JavaFX no Windows; não houve acesso a um desktop Windows nesta tarefa.

**Limites das evidências:** os testes unitários comprovam a decisão de seleção, mas não a renderização sem Scene3D. Não foi adicionado teste de integração gráfico à suíte headless. O CI no Windows comprova compilação e testes, não a abertura real da janela. Manter a issue #1 aberta e usar `Refs #1` na PR enquanto o aceite gráfico obrigatório não estiver comprovado; G1 continua fora desta correção.
