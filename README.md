# NodeWalker

**A 3D visualization of the Traveling Salesman Problem**

Aplicação desktop Java para comparar **força bruta** e **backtracking com poda**, com visualização JavaFX 3D e alternativa 2D.

> **Estado atual — fundação G0:** o repositório contém uma demonstração gráfica ilustrativa e um cálculo inicial do espaço de busca. **Os solvers, o gerador de cidades, o mapa conectado ao TSP e o controle de execução ainda não estão implementados.** Não confundir a rota ilustrativa do G0 com um resultado ótimo.

## Requisitos

- JDK 21 (`java -version` / `javac -version`)
- Apache Maven 3.9+ (`mvn -version`)
- Fedora Linux ou Windows com ambiente de desktop para executar JavaFX

## Compilar e testar

Na raiz do repositório:

```bash
mvn clean verify
```

A suíte contém 70 execuções de testes, incluindo casos parametrizados: três cobrem `(N-1)!`, quatro cobrem a seleção inicial 3D/2D e 63 cobrem o modelo de domínio, a imutabilidade defensiva e a soma segura de custos. Todos são executados sem abrir uma janela JavaFX.

## Executar a demonstração do G0

O primeiro comando instala o módulo `tsp-core` no repositório local do Maven; o segundo abre a UI de `tsp-app`.

```bash
mvn clean install
mvn -f tsp-app/pom.xml javafx:run
```

Clique em **Alternativa 2D** para verificar a versão bidimensional mesmo se a placa de vídeo suportar Scene3D. Se não suportar, o app inicia automaticamente em 2D. O G0 apenas detecta a capacidade; não substitui os testes manuais no Fedora e no Windows.

Para iniciar explicitamente em 2D:

```bash
mvn -f tsp-app/pom.xml javafx:run -Djavafx.args="--2d"
```

> Em máquinas Fedora sem bibliotecas gráficas e drivers configurados, o JavaFX pode não conseguir abrir janela. Isso é um requisito do ambiente, não do algoritmo TSP.

## Estrutura

```text
NodeWalker/
├── .github/workflows/ci.yml
├── docs/                     # especificações v0.4 e notas técnicas
├── tsp-core/                 # algoritmo e modelo, sem JavaFX
├── tsp-app/                  # interface JavaFX
├── pom.xml                   # Maven multimódulo
└── README.md
```

## Roadmap e aceite

- **G0:** Maven multimódulo, Java 21, JavaFX 21.0.12, JUnit 5.14.3, hello 3D + fallback 2D. **Compilação corrigida e sete testes aprovados; demonstração original validada no Fedora. Evidências e pendências finais de CI/interface: [roadmap](docs/ROADMAP.md).**
- **G1:** modelo do grafo completo, custos, geração por seed, força bruta, testes e CLI de benchmark.
- **G2:** backtracking com poda exata, métricas, limites, cancelamento, teste diferencial.
- **G3:** MVP visual funcional, com solvers conectados à UI em thread separada.
- **G4/G5:** camada didática e efeitos, apenas após o MVP estar estável.

Leia [docs/ROADMAP.md](docs/ROADMAP.md) e [docs/REVISAO_TECNICA.md](docs/REVISAO_TECNICA.md) antes de expandir o escopo.

## Fonte das especificações

Os documentos `DOC_01`, `DOC_02` e `DOC_03` (v0.4, revisados em 06/10/2026) são a fonte normativa. NodeWalker é o nome do produto; “TSP Delivery Route System” é o título de trabalho preservado nos documentos originais.
