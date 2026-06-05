# Coverage patches batch 46

## Conteúdo

- `patches/apply_add_web_rest_and_character_naming_coverage_tests.sh`
- `patches/apply_raise_jacoco_line_coverage_gate_to_89.sh`
- `coverage_patches_batch_46_manifest.md`

## Objetivo

Adicionar cobertura de baixo risco em pontos que ainda tinham lacunas pequenas após o Batch 45:

- `OnlineController`
- `AuthController`
- `CharacterNamingService`

## Testes adicionados

### OnlineControllerCoverageTest

Cobre:

- retorno de total online atual;
- mapeamento de mundos com scrape mais recente;
- fallback para `playersOnline=0` quando não há scrape recente;
- consulta de mundo específico;
- histórico com intervalo explícito em epoch millis.

### AuthControllerCoverageTest

Cobre:

- registro com senha codificada e role padrão `USER`;
- rejeição de username duplicado;
- login com emissão de access/refresh token;
- refresh token inválido ou que não é do tipo `refresh`;
- refresh token com usuário ausente, revogado e expirado;
- rotação de refresh token válida;
- logout idempotente e revogação de bearer token válido.

### CharacterNamingServiceCoverageTest

Cobre:

- `ensureCharacterForName` sem former names;
- `ensureCharacterForName` com former names;
- delegações diretas para resolver nome observado e reconciliar nomes oficiais;
- `handleRenamed` com e sem nome antigo.

## Gate de cobertura

O segundo patch sobe o gate de line coverage do JaCoCo de `88%` para `89%`.

O relatório do Batch 45 já mediu aproximadamente:

- `249` testes;
- `89%` instruction coverage;
- `66%` branch coverage;
- `89%` line coverage;
- `application.service` em aproximadamente `92%`;
- `infrastructure.adapter.scraper` em aproximadamente `83%`;
- `infrastructure.adapter.web.rest` em aproximadamente `75%`.

## Validação recomendada

```bash
bash patches/apply_add_web_rest_and_character_naming_coverage_tests.sh
make test

bash patches/apply_raise_jacoco_line_coverage_gate_to_89.sh
make qa
make test-coverage
```

## Resultado esperado

- Testes: `249` -> aproximadamente `264`.
- Gate JaCoCo: `88%` -> `89%`.
- Ganho esperado principalmente em:
  - `infrastructure.adapter.web.rest`;
  - `application.service`.
