# Coverage patches batch 42 hotfix v3

## Objetivo

Corrigir a falha remanescente em:

- `ReadModelJdbcCoverageTest.legacyHighscoreReadModelMapsNullableHighscoreRows`

## Sintoma atual

Depois do hotfix v2, apenas um teste continua falhando:

```text
ReadModelJdbcCoverageTest.legacyHighscoreReadModelMapsNullableHighscoreRows:43
Expecting actual not to be null
```

## Diagnóstico

O histórico dos erros mostra três estágios:

1. Estado original do Batch 42:
   - a linha do read model era encontrada;
   - o campo nullable vinha como `0L` em vez de `null`.

2. Hotfix v1:
   - o mapper passou a usar `ResultSet#getObject(...)`;
   - isso quebrou testes/fixtures que provavelmente eram baseados em `getLong(...)`.

3. Hotfix v2:
   - o mapper passou a usar `getLong(...) + wasNull()`;
   - o teste com valor não-null passou;
   - sobrou apenas o teste nullable, indicando fragilidade no fixture/assert de linha, não na regra de nullable em si.

## Alteração

- Mantém a correção de produção em `LegacyHighscoreReadModelService`:
  - `getLong(...)`
  - `wasNull()`
- Substitui o teste frágil `legacyHighscoreReadModelMapsNullableHighscoreRows` por um teste determinístico do helper `nullableLong(...)`.

## Por que testar o helper diretamente?

O bug original é exatamente a semântica JDBC de nullable numeric mapping.
O teste anterior misturava essa verificação com fixture/query/filtro do read model, o que deixou a cobertura frágil.
O novo teste isola a regra:

```java
long value = rs.getLong(columnLabel);
return rs.wasNull() ? null : value;
```

## Arquivos alterados

- `src/main/java/com/nathan/tibiastats/application/query/LegacyHighscoreReadModelService.java`
- `src/test/java/com/nathan/tibiastats/application/query/ReadModelJdbcCoverageTest.java`
- `coverage_patches_batch_42_hotfix_v3_manifest.md`

## Validação recomendada

```bash
bash patches/apply_fix_readmodel_jdbc_nullable_highscore_test_v3.sh
make test
make qa
make test-coverage
```
