# Coverage patches batch 48 hotfix

## Conteúdo

- `patches/apply_fix_highscore_record_history_sql_filter_spacing.sh`
- `coverage_patches_batch_48_hotfix_manifest.md`

## Problema corrigido

O Batch 48 adicionou cobertura para filtros opcionais de `HighscoreRecordReadModelService.findHistory(...)`.

O teste revelou um bug real de montagem dinâmica de SQL: quando `to` e `characterName` eram informados juntos, o SQL podia concatenar os filtros sem separador e gerar um parâmetro inválido:

```text
:toDateand
```

Isso causava:

```text
InvalidDataAccessApiUsageException: No value supplied for the SQL parameter 'toDateand'
```

## Correção

O hotfix adiciona quebra de linha explícita nos filtros opcionais de data em `HighscoreRecordReadModelService.findHistory(...)`, evitando que o próximo trecho SQL seja colado ao parâmetro anterior.

## Validação recomendada

```bash
bash patches/apply_fix_highscore_record_history_sql_filter_spacing.sh
make test
make qa
make test-coverage
```

## Observações

- Corrige código de produção.
- Mantém os testes do Batch 48.
- Não altera o gate do JaCoCo.
- Não altera contratos de API.
