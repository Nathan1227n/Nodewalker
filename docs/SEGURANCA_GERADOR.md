# Segurança da geração TSP — issue #25

A [issue #25](https://github.com/Nathan1227n/Nodewalker/issues/25) complementa o gerador determinístico da issue #3. O espaço de coordenadas é finito e a separação mínima pode tornar a geração impraticável; matrizes e cópias defensivas consomem memória O(N²). A API controlada permite interromper essa fase antes de existir uma instância completa. Está implementada e aguarda revisão; G1 continua em andamento.

## API e resultados

```java
TspGenerator.generate(int n, long seed)
TspGenerator.generate(int n, long seed, GenerationOptions options)
TspGenerator.estimatePeakMemoryBytes(int n) // BigInteger
```

A primeira assinatura preserva o contrato original: N >= 3, sem orçamento implícito de tentativas ou memória e sem observar interrupção da thread. A sobrecarga também exige N >= 3 e retorna somente um `TspProblem` completo, validado e imutável. Não retorna cidades ou matriz parciais, nem utiliza `TspResult` para representar interrupção de geração.

`GenerationOptions(long maxCandidates, long maxEstimatedBytes, GenerationCancellationToken cancellationToken)` exige os dois orçamentos positivos e token não nulo. O construtor de dois argumentos cria um token novo. Os orçamentos são imutáveis; o token é um sinal mutável explícito, thread-safe, baseado em `AtomicBoolean`. `cancel()` é persistente e idempotente, sem reset. Uma operação independente deve usar um token novo; compartilhar um token permite cancelar conjuntamente as gerações que o observam. Os contadores e o `Random` pertencem a cada chamada, mesmo se as opções forem reutilizadas.

| Situação | Comportamento |
| --- | --- |
| N < 3 ou orçamento não positivo | `IllegalArgumentException` |
| Opções ou token nulos | `NullPointerException` |
| Orçamento de candidatos menor que N ou esgotado antes do próximo sorteio | `GenerationLimitExceededException` |
| Estimativa acima do orçamento de bytes | `GenerationMemoryLimitExceededException` |
| Token cancelado ou interrupção da thread observados | `GenerationCancelledException`, derivada de `CancellationException` |
| Outra falha | Propagada, sem ser convertida em limite, cancelamento ou sucesso |

O limite de tentativas expõe `maxCandidates()`, `attemptedCandidates()` e `acceptedCities()`. O cancelamento expõe os dois contadores; a rejeição de memória expõe `estimatedBytes()` (`BigInteger`) e `maxEstimatedBytes()`. Um candidato contado consumiu um trio completo x/y/z. Em cancelamento, esse candidato pode ainda não ter sido classificado: tentativas menos cidades aceitas não é necessariamente o total de rejeições geométricas. No preflight nenhum candidato foi sorteado. Cancelamento é verificado antes dos orçamentos, após a validação de N e das opções; orçamento de candidatos é verificado antes do orçamento de memória.

## Preflight de memória e complexidade

Antes de criar a lista de cidades ou matrizes, a sobrecarga verifica `maxCandidates >= N` e calcula exatamente, em `BigInteger`:

```text
estimatedPeakBytes = 16*N² + 256*N + 1024
```

O termo quadrático inclui os elementos de duas matrizes `long`: a construída pelo gerador e a cópia defensiva do modelo. A reserva linear/fixa estima cidades, referências, cabeçalhos de arrays e cópias temporárias de listas. É um modelo portátil explícito, não uma medição do layout da JVM nem uma reserva de heap. A estimativa pode ultrapassar `Long.MAX_VALUE` sem overflow; como o orçamento é `long` positivo, essa solicitação é rejeitada. O orçamento é inclusivo: igualdade é permitida. Exemplos testados: N=3 exige 1936 bytes estimados; N=4, 2304; N=100, 186624. N=`Integer.MAX_VALUE` produz exatamente 73786976775874544400 bytes estimados.

Não existe N máximo universal. O chamador escolhe o orçamento adequado à memória disponível e às demais operações da aplicação. A API não captura `OutOfMemoryError` como controle normal e não garante sua ausência: heap disponível, layout da JVM e alocações concorrentes podem tornar uma solicitação inviável mesmo quando passa pelo modelo.

A quantidade de candidatos limita o trabalho de seleção; cada candidato faz no máximo N-1 comparações de separação. A matriz calcula N*(N-1)/2 pares e o modelo valida N² células. Portanto, o trabalho é O(maxCandidates*N + N²); o orçamento quadrático também limita o tamanho dessas fases. Não há limite de tempo, garantia de latência ou preflight fatorial de solver nesta API. Não é calculado um produto potencialmente excedente em `long` para tomar decisões: o preflight de memória usa `BigInteger`, e o contador só é incrementado após verificar que está abaixo do limite.

## Determinismo e tentativas rejeitadas

Cada geração continua criando exatamente um `new Random(seed)`. Cada candidato consome `nextInt(0, 1001)` para **x, depois y, depois z**, inclusive quando rejeitado. Não há checkpoint entre esses três sorteios. Depois do trio, o controle registra uma tentativa e verifica cancelamento; antes do próximo trio, verifica o limite. Os checkpoints não sorteiam valores e não reinicializam o `Random`.

Os critérios geométricos permanecem os do DOC 03 §§3 e 18: rejeitar distância 3D ao quadrado < 2500; aceitar distância exatamente 50. Custos continuam sendo `Math.round(Math.sqrt(dx² + dy² + dz²))`, calculados uma vez por par e copiados simetricamente. Quando a geração controlada termina com sucesso, os mesmos N e seed reproduzem cidades, ordem, índices, origem, seed e custos da API original.

### Referência N=4, seed=657

```java
GenerationOptions options = new GenerationOptions(5, 2304);
TspProblem problem = TspGenerator.generate(4, 657L, options);
```

| Índice | x | y | z |
| --- | --- | --- | --- |
| 0 | 930 | 591 | 463 |
| 1 | 188 | 89 | 36 |
| 2 | 284 | 169 | 46 |
| 3 | 232 | 587 | 882 |

```text
       0    1    2    3
0      0  992  877  814
1    992    0  125  983
2    877  125    0  936
3    814  983  936    0
```

São cinco tentativas, das quais uma é rejeitada: o terceiro trio `(936,637,445)` tem distância ao quadrado 2476 para a cidade 0. Com orçamento de quatro candidatos, a geração falha com `attemptedCandidates() == 4` e `acceptedCities() == 3`; não sorteia o quinto trio nem retorna um problema parcial. Os testes comparam as coordenadas e custos acima com literais de referência, preservando também os testes independentes da issue #3.

## Cancelamento durante todas as fases

O controle observa o token e `Thread.currentThread().isInterrupted()` antes de alocações, entre candidatos, nas comparações de separação, antes/depois de alocar cada linha e entre pares da matriz. A interrupção não é limpa. Não são usados `Thread.stop`, sleeps, JavaFX ou estado global.

A fábrica adicional `TspProblem.copyOf(List<City>, long[][], long seed, Runnable checkpoint)` preserva as validações e cópias defensivas existentes, executando o callback antes/depois de cópias e nos laços de validação. Ela permite interromper também a segunda matriz e a validação final. O modelo depende somente de `Runnable`, sem importar tipos do gerador. O callback deve apenas verificar controle de execução, sem modificar entradas, e não é retido no objeto. Falhas do callback são propagadas. Os construtores antigos continuam disponíveis e usam um callback vazio.

Uma última verificação ocorre após construir o problema e antes de retorná-lo. O cancelamento é cooperativo: uma alocação, clone de linha ou cópia de lista já iniciados precisam terminar antes do próximo checkpoint. Não há garantia de resposta em um tempo fixo. Um pedido posterior à última verificação pode coincidir com uma conclusão bem-sucedida.

## Integração futura

A futura UI deverá usar a API controlada com orçamento calibrado por testes práticos e memória disponível, executando a geração fora da JavaFX Application Thread. Ela deverá tratar limite, cancelamento e erro separadamente e descartar qualquer operação que não tenha retornado uma instância completa.

Geração e execução de solver são fases distintas. Cancelar a geração não cria um resultado de solver cancelado: ainda não há problema pronto. Limites, cancelamento e listeners dos solvers continuam na [issue #9](https://github.com/Nathan1227n/Nodewalker/issues/9); o preflight da aplicação também deverá considerar o crescimento `(N-1)!`. Nesta entrega não foram implementados solvers, UI ou benchmark.

## Evidências

Validação local em Fedora 44, Temurin 21.0.12.1 e Maven 3.9.11:

- `mvn -pl tsp-core test`: 357 testes aprovados.
- `mvn clean verify`: BUILD SUCCESS no pai, núcleo e aplicação; 361 testes aprovados, sem falhas, erros ou ignorados.
- 75 casos novos: 10 de opções/token, 46 de limites/determinismo/memória, 12 de cancelamento e sete de cópia cooperativa. Todos os 286 testes anteriores foram preservados.
- Cobertura de N=3/4/10/100; seeds 0/1/-1/extremos/657; referências explícitas com rejeição; limite inclusivo; preflight de tamanhos extremos sem entrar na geração; cancelamento antes/durante sorteio, alocação, custos, cópia/validação e devolução final.
- Cancelamento entre threads usa barreiras `CountDownLatch`, tanto para token quanto para interrupção, sem depender de sleeps. Os contadores, ausência de retorno parcial e preservação da interrupção são verificados.

O [CI de push](https://github.com/Nathan1227n/Nodewalker/actions/runs/38334456772) e o [CI da PR #26](https://github.com/Nathan1227n/Nodewalker/actions/runs/38334513100) passaram em Ubuntu e Windows no commit de implementação `06c26e6`. Os logs da PR confirmam 357 testes do núcleo e quatro da aplicação por sistema, sem falhas, erros ou ignorados, com BUILD SUCCESS em todos os módulos. `git diff --check` passou; não há arquivos `target/` versionados, remoção de testes anteriores ou dependências gráficas no núcleo.

A [PR #26](https://github.com/Nathan1227n/Nodewalker/pull/26) está aberta para revisão, sem merge automático ou encerramento da issue #25. Não foi alterada a documentação visual da PR #24; a atualização desta etapa se limita a este documento e à revisão técnica. A issue #5 não foi iniciada.
