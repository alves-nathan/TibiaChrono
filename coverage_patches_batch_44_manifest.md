# Coverage patches batch 44

## Objetivo

Adicionar cobertura de baixo risco para facades/serviços de aplicação ainda parcialmente descobertos e elevar o gate do JaCoCo de 86% para 87%, com base na cobertura real medida após o Batch 43.

## Scripts

- `patches/apply_add_service_facade_coverage_tests.sh`
- `patches/apply_raise_jacoco_line_coverage_gate_to_87.sh`

## Testes adicionados

- `HighscoreServiceCoverageTest`
- `CharacterDetailsServiceCoverageTest`
- `AdminScraperServiceCoverageTest`

## Classes cobertas/reforçadas

- `HighscoreService`
  - delegação para plano legado `default`;
  - skip por configuração global desabilitada;
  - skip por plano desabilitado;
  - skip por backoff HTTP ativo;
  - proteção contra execução concorrente via `AtomicBoolean`;
  - delegação dos acessores/reset de backoff.

- `CharacterDetailsService`
  - retorno vazio quando o selector não encontra nomes;
  - delegação para o processor quando há nomes selecionados.

- `AdminScraperService`
  - delegação de status;
  - status/reset de backoff;
  - gatilhos manuais de worlds, character details, guilds e highscore plan.

## Gate JaCoCo

O Batch 43 validado mediu aproximadamente:

- 234 testes;
- 88% instruction coverage;
- 64% branch coverage;
- 88% line coverage;
- `application.service` em aproximadamente 90%;
- `infrastructure.persistence` em aproximadamente 89%.

Por isso, este batch sobe o gate de line coverage de `0.86` para `0.87`, mantendo margem segura abaixo da cobertura real atual.

## Validação recomendada

```bash
bash patches/apply_add_service_facade_coverage_tests.sh
make test

bash patches/apply_raise_jacoco_line_coverage_gate_to_87.sh
make qa
make test-coverage
```
