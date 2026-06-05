# Coverage patches batch 46 hotfix

## Conteúdo

- `patches/apply_fix_auth_controller_coverage_nested_mockito_stubbing.sh`
- `coverage_patches_batch_46_hotfix_manifest.md`

## Problema corrigido

O Batch 46 adicionou `AuthControllerCoverageTest` com chamadas do tipo:

```java
when(jwt.parse("token")).thenReturn(jwsWith(claims));
```

O helper `jwsWith(...)` também faz stubbing de Mockito. Como ele era chamado dentro do argumento de `thenReturn(...)`,
o Mockito detectava stubbing aninhado/inacabado e lançava `UnfinishedStubbingException`.

## Correção

O hotfix troca esse padrão por:

```java
doReturn(jwsWith(claims)).when(jwt).parse("token");
```

Com isso o helper é executado antes da configuração do stub externo, evitando o stubbing aninhado.

## Validação recomendada

```bash
bash patches/apply_fix_auth_controller_coverage_nested_mockito_stubbing.sh
make test
make qa
make test-coverage
```

## Observações

- Não altera produção.
- Não altera o gate do JaCoCo.
- Não altera os testes de `OnlineControllerCoverageTest` nem `CharacterNamingServiceCoverageTest`.
