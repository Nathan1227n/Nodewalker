# Proposta futura: grafos direcionados e execução didática

Registrada em 20/10/2026; **não implementada nesta PR**. Depende da revisão dos slides originais e de aprovação própria. Não inicia a homologação da issue #13 nem conclui o rastreamento interno previsto na #14.

O exercício informado de “6-AlgDijkstra.pdf”, pp. 66–67, é direcionado e tem resposta 0–2–3, custo 9. O contrato atual de `TspProblem` é simétrico e o de `RefugeTopology` não direcionado. Reutilizá-los silenciosamente para esse exemplo mudaria o problema matemático.

Uma entrega separada deverá definir um grafo ponderado direcionado para caminhos mínimos, com adjacência de saída explícita e pesos não negativos, independente do contrato TSP. Deve-se decidir como compartilhar cidades/coordenadas sem obrigar pesos orientados a caber na matriz simétrica. O solver clássico poderá ganhar adaptação específica somente com testes dirigidos independentes, conferindo os slides originais, ausência de aresta reversa implícita, distâncias `BigInteger`, predecessores, cancelamento e preflight. A visualização precisará de setas e tratamento de pares de arcos em sentidos opostos.

Outra entrega poderá registrar eventos internos limitados de fixação/relaxamento e de permutação/poda, com contrato de trilha, orçamento, truncamento e reprodução separados do resultado. Deve preservar a matemática, métricas internas e cancelamento. A reprodução atual apenas revela um resultado pronto; ela não pode ser renomeada como rastreamento do algoritmo. Revisar previamente os slides para definir o que deverá ser demonstrado e evitar modelos ou instrumentação antecipados.
