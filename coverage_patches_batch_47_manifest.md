# Coverage patches batch 47

## Conteúdo

- `patches/apply_add_scraper_adapter_and_highscore_persistence_coverage_tests.sh`
- `patches/apply_raise_jacoco_line_coverage_gate_to_90.sh`
- `coverage_patches_batch_47_manifest.md`

## Objetivo

Adicionar cobertura de baixo risco em wrappers/adapters e em um repositório de persistência simples, sem rede externa e sem banco real:

- `JsoupCharacterAdapter`
- `JsoupScrapeAdapter`
- `JsoupHighscoreAdapter`
- `JsoupGuildAdapter`
- `SpringHighscorePersistenceRepository`

## Testes adicionados

### JsoupAdapterCoverageTest

Cobre:

- `JsoupCharacterAdapter`
  - retorno de `NameDetails` a partir de `CharacterDetails`;
  - fallback para o nome solicitado quando o parser retorna vazio;
  - mapeamento de `HttpStatusException` e `IOException` para `RuntimeException`.

- `JsoupScrapeAdapter`
  - delegação de overview, world page e former name;
  - wrapping de `IOException` em todos os caminhos públicos.

- `JsoupHighscoreAdapter`
  - delegação para client HTTP e parser;
  - wrapping de `IOException`;
  - tratamento de `InterruptedException` restaurando a flag de interrupção.

- `JsoupGuildAdapter`
  - delegação de listagem e detalhes;
  - wrapping de `IOException` com contexto de lista/detalhe.

### SpringHighscorePersistenceRepositoryCoverageTest

Cobre:

- criação do native upsert;
- parâmetros enviados para `Query#setParameter`;
- execução do `executeUpdate`.

## Gate de cobertura

O segundo patch sobe o gate de line coverage do JaCoCo de `89%` para `90%`.

O relatório do Batch 46 já mediu aproximadamente:

- `264` testes;
- `90%` instruction coverage;
- `67%` branch coverage;
- `90%` line coverage;
- `application.service` em aproximadamente `92%`;
- `infrastructure.adapter.web.rest` em aproximadamente `84%`;
- `infrastructure.adapter.scraper` em aproximadamente `83%`;
- `infrastructure.persistence` em aproximadamente `89%`.

## Validação recomendada

```bash
bash patches/apply_add_scraper_adapter_and_highscore_persistence_coverage_tests.sh
make test

bash patches/apply_raise_jacoco_line_coverage_gate_to_90.sh
make qa
make test-coverage
```

## Resultado esperado

- Testes: `264` -> aproximadamente `274`.
- Gate JaCoCo: `89%` -> `90%`.
- Ganho esperado principalmente em:
  - `infrastructure.adapter.scraper`;
  - `infrastructure.persistence`.
