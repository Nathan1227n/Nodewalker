# Roadmap / controle de gates

**Entrega planejada:** 19/10/2026. **Escopo do MVP oficial:** G0–G3. Não tratar G1 ou G2 isoladamente como MVP visual.

| Gate | Conteúdo | Critério de saída | Situação em 11/10 |
| --- | --- | --- | --- |
| G0 | Maven multimódulo, Java 21, JavaFX 21.0.12, JUnit 5.14.3, hello 3D/fallback | Compilação, testes e demonstração no Fedora/Windows | Scaffold preparado, validação pendente |
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

## G0: checklist de verificação local

- [ ] `java -version` indica JDK 21.
- [ ] `mvn -version` indica Maven e Java 21.
- [ ] `mvn clean verify` funciona em Fedora.
- [ ] `mvn -f tsp-app/pom.xml javafx:run` abre a janela depois de `mvn clean install`.
- [ ] A visualização 3D aparece no hardware com suporte.
- [ ] Clicar em “Alternativa 2D” exibe o mapa bidimensional.
- [ ] Build/teste passa no Windows.
- [ ] Fallback 2D é testado quando Scene3D não está disponível, não apenas pelo botão manual.

**Nota:** CI de GitHub Actions compila/testa em Ubuntu e Windows, mas não comprova renderização Scene3D. Testes em Fedora e fallback real continuam necessários.
