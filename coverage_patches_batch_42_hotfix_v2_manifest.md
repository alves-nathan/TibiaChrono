# Coverage patches batch 42 hotfix v2

## Objetivo

Corrigir a regressão do primeiro hotfix do Batch 42 nos testes de `ReadModelJdbcCoverageTest`.

## Sintoma

Depois do primeiro hotfix, o erro deixou de ser `expected: null but was: 0L` e passou para:

- `legacyHighscoreReadModelMapsNullableHighscoreRows`: `Expecting actual not to be null`
- `legacyHighscoreReadModelMapsExactDateRowsWithNonNullValue`: `Expecting actual not to be null`

## Causa provável

O primeiro hotfix trocou leituras numéricas para um helper baseado em `ResultSet#getObject(...)`.
Isso preserva `NULL` em JDBC real, mas pode quebrar testes que exercitam o mapper com `ResultSet`
mockado, porque esses testes normalmente stubam `getLong(...)` e `wasNull()`, não `getObject(...)`.

## Alteração

Mantém o helper `nullableLong(...)`, mas troca a implementação para o padrão JDBC clássico:

```java
private static Long nullableLong(ResultSet rs, String columnLabel) throws SQLException {
    long value = rs.getLong(columnLabel);
    return rs.wasNull() ? null : value;
}
```

Assim o mapper:

- preserva `NULL` corretamente;
- continua compatível com `ResultSet` real;
- continua compatível com testes baseados em mock de `getLong(...)`/`wasNull()`.

## Arquivos alterados

- `src/main/java/com/nathan/tibiastats/application/query/LegacyHighscoreReadModelService.java`
- `coverage_patches_batch_42_hotfix_v2_manifest.md`

## Validação recomendada

```bash
bash patches/apply_fix_legacy_highscore_readmodel_nullable_long_mapping_v2.sh
make test
make qa
make test-coverage
```
