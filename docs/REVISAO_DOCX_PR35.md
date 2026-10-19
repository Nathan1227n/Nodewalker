# Comparação dos DOCX da PR #35 com a main — 19/10/2026

Base conferida: `main` no commit `14a8a5a3c8b68dfce2e2e72bda860d5b291b78fd` (merge da PR #34).
Documentos revisados: versão publicada na PR #35 em `6836bb84ee9b96abd9d7888e4b9e3101700280b5`.
Esta revisão de código não altera novamente os três DOCX.

## Resultado da comparação

| Documento | Parágrafos na main | Parágrafos na PR | Acrescentados | Reescritos/removidos |
| --- | ---: | ---: | ---: | ---: |
| DOC 01 — Visão, Objetivos e Escopo | 213 | 223 | 10 | 0 |
| DOC 02 — Requisitos do Sistema | 507 | 517 | 10 | 0 |
| DOC 03 — Especificação do TSP | 318 | 328 | 10 | 0 |

A contagem considera todos os elementos `w:p` de `word/document.xml`, incluindo parágrafos em tabelas.
Em cada documento foi acrescentado o mesmo bloco final **“Revisão de escopo acadêmico — 19/10/2026 (após a PR #34)”**, antes das propriedades finais de seção. Nenhuma seção histórica foi movida ou substituída.

## Resumo dos dez parágrafos acrescentados em cada documento

| Posição no novo bloco | Conteúdo |
| ---: | --- |
| 1 | Título e data da revisão acadêmica após a PR #34. |
| 2 | Dois problemas separados: força bruta/backtracking para ciclo TSP; Dijkstra clássico para caminhos mínimos entre refúgios. Declara explicitamente que um caminho de Dijkstra não é uma solução do TSP. |
| 3 | Alteração do escopo geral para incluir Dijkstra, preservando os contratos TSP; registra que a proposta sobre máscaras/estados expandidos não foi implementada. |
| 4 | Grafo/topologia separados, mesmos índices e custos originais; completa, esparsa por seed e manual desconectada; terreno não define custo. |
| 5 | Heap indexado, extração mínima, fixação, relaxamentos e predecessores; distâncias `BigInteger`; contratos separados de `TspResult`/`TspSolver`. |
| 6 | Desempate determinístico sem promessa lexicográfica; predecessores sem ciclos, inclusive com pesos zero. |
| 7 | Confirmação somente após fixação, ocultação de tentativos, inalcançável somente após conclusão, estados terminais e limites cooperativos. |
| 8 | Complexidades de força bruta/backtracking e Dijkstra; custo da aritmética, topologia e matriz O(N²); estimativas não representam heap real reservado. |
| 9 | Auditoria Floyd–Warshall exclusivamente em testes e cobertura de extremos, interrupções, reprodução e preservação do baseline TSP. |
| 10 | Separação das PRs A/B/C e das issues #12/#13; referência a `docs/CAMINHOS_MINIMOS.md`. |

Não foram alteradas as definições anteriores do ciclo hamiltoniano, retorno à origem, custos, desempate ou poda do TSP. A inclusão acadêmica de Dijkstra aplica-se exclusivamente ao novo problema de caminhos mínimos.

## Verificação estrutural

A comparação foi feita abrindo cada DOCX como ZIP e analisando seu XML, sem regravar os arquivos:

1. Lista de partes ZIP idêntica entre base e PR.
2. A única parte com conteúdo diferente é `word/document.xml`.
3. Todos os parágrafos históricos permanecem idênticos, na mesma ordem, seguidos pelos dez novos.
4. Remover exatamente os dez elementos XML acrescentados recupera **byte a byte** o `word/document.xml` da main.
5. As demais partes ZIP permanecem idênticas, incluindo estilos, relacionamentos, propriedades e quaisquer recursos existentes.

Assim, a preservação não foi conferida apenas por extração de texto: o XML histórico e os demais conteúdos do pacote também foram confrontados.

## Identificação dos arquivos auditados (SHA-256)

```text
DOC_01_Visao_Objetivos_Escopo_TSP.docx
main: 83f3d0dd5ad67a1543abbc0b161dc365e56b3d600f656226734f583185844c46
PR:   c73e67c59f4f8e549ed01e8c73d6d4cbb6637e88c998ae7f9cd87990435909d4

DOC_02_Requisitos_do_Sistema_TSP.docx
main: 643aa71c57edaeb234e4c7e0d5e0bd4781884e8c6fae34966596a9ca156d4103
PR:   e658ec3e108af79e8c5eed07c5c54fc3afe3de8f0f8ccfc4cff241a75efc1007

DOC_03_Especificacao_do_TSP.docx
main: 17586433f77b89043334aa4970f9f9c3f74cbe0cbd706410d2ef2d37b00fcc83
PR:   bd24818d74a025a1a9e98faf4a40488513ac8f48f1dc451e962ae8b4ec6d9759
```
