# Coverage patches batch 42 hotfix

## Objetivo

Corrigir a falha do teste `ReadModelJdbcCoverageTest.legacyHighscoreReadModelMapsNullableHighscoreRows`.

## Causa raiz

`ResultSet#getLong(...)` converte SQL `NULL` em `0L` quando o mapper não chama `ResultSet#wasNull()`.
Como o read model legado de highscore possui campos numéricos nullable, o mapper precisa preservar `NULL`
em vez de expor `0L`.

## Alteração

- Atualiza `LegacyHighscoreReadModelService`
- Substitui leituras `rs.getLong(...)` por `nullableLong(rs, ...)`
- Adiciona helper JDBC local:
  - `nullableLong(ResultSet rs, String columnLabel)`

## Arquivos alterados

- `src/main/java/com/nathan/tibiastats/application/query/LegacyHighscoreReadModelService.java`
- `coverage_patches_batch_42_hotfix_manifest.md`

## Validação recomendada

```bash
bash patches/apply_fix_legacy_highscore_readmodel_nullable_long_mapping.sh
make test
make qa
make test-coverage
```
