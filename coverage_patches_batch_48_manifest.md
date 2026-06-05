# Coverage patches batch 48

## Conteúdo

- `patches/apply_add_highscore_api_and_character_identity_readmodel_coverage_tests.sh`
- `patches/apply_raise_jacoco_line_coverage_gate_to_91.sh`
- `coverage_patches_batch_48_manifest.md`

## Objetivo

Adicionar cobertura de baixo risco no pacote `application.query`, especialmente nos pontos que ainda concentravam linhas descobertas após o Batch 47:

- `HighscoreApiJdbcSupport`
- `CharacterIdentityReadModelService`
- validações e filtros opcionais dos read models de highscore

## Testes adicionados

### HighscoreApiJdbcSupportCoverageTest

Cobre:

- mapeamento de `ExperienceDailyView`;
- mapeamento de `ExperienceGainView`;
- mapeamento de `CurrentHighscoreView`;
- mapeamento de `PeriodHighscoreView`;
- fallback `UNKNOWN` para categoria desconhecida;
- todos os códigos de `StatCategory`;
- normalização obrigatória com trim;
- rejeição de valores obrigatórios em branco.

### CharacterIdentityReadModelCoverageTest

Cobre:

- `findCharacter` com mapeamento de detalhes e campos nullable;
- `findCharacterNames(String)` quando o character não é resolvido;
- `findCharacterNames(String)` resolvendo character e depois nomes;
- `findCharacterNames(Long)` com nome ativo sem inactive date.

### HighscoreApiReadModelValidationCoverageTest

Cobre:

- validações de datas em experience gains;
- validação de world obrigatório;
- filtros opcionais de character/world/date;
- rejeição de categoria ausente;
- rejeição de `EXPERIENCE` nos endpoints de records;
- history com filtros opcionais.

## Gate de cobertura

O segundo patch sobe o gate de line coverage do JaCoCo de `90%` para `91%`.

O relatório do Batch 47 já mediu aproximadamente:

- `274` testes;
- `91%` instruction coverage;
- `67%` branch coverage;
- `91%` line coverage;
- `infrastructure.adapter.scraper` em aproximadamente `88%`;
- `infrastructure.persistence` em aproximadamente `91%`;
- `application.query` em aproximadamente `89%`.

## Validação recomendada

```bash
bash patches/apply_add_highscore_api_and_character_identity_readmodel_coverage_tests.sh
make test

bash patches/apply_raise_jacoco_line_coverage_gate_to_91.sh
make qa
make test-coverage
```

## Resultado esperado

- Testes: `274` -> aproximadamente `285`.
- Gate JaCoCo: `90%` -> `91%`.
- Ganho esperado principalmente em:
  - `application.query`;
  - cobertura total de line/instruction.
