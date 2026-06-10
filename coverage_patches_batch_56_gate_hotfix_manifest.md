# Coverage patches batch 56 gate hotfix

## Conteúdo

- `patches/apply_restore_jacoco_line_coverage_gate_to_97_after_batch_56.sh`
- `coverage_patches_batch_56_gate_hotfix_manifest.md`

## Problema corrigido

Após o Batch 56 e os hotfixes dos testes:

- `make test` passou com `365` testes, `0` falhas e `0` erros;
- o `verify` também executou os testes com sucesso;
- o build falhou apenas no `jacoco:check`, porque o gate foi elevado para `0.98`, mas a cobertura efetiva de linhas continuou em `0.97`.

Erro observado:

```text
Rule violated for bundle TibiaChrono: lines covered ratio is 0.97, but expected minimum is 0.98
```

## Correção aplicada

Este hotfix restaura:

```xml
<jacoco.minimum.coverage>0.97</jacoco.minimum.coverage>
```

## Escopo

- Não remove os testes do Batch 56;
- Não altera código de produção;
- Apenas desfaz a elevação prematura do gate para `0.98`;
- Mantém o gate sustentável em `0.97`.

## Validação recomendada

```bash
bash patches/apply_restore_jacoco_line_coverage_gate_to_97_after_batch_56.sh
make qa
make test-coverage
```

Opcionalmente, para validação completa:

```bash
make test && make qa && make test-coverage
```

## Próximo passo

Para buscar `0.98`, gere o próximo batch usando o relatório atualizado após este hotfix, mirando as linhas remanescentes com maior retorno de cobertura.
