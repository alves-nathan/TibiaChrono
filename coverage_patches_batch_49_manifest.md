# Coverage patches batch 49

## Conteúdo

- `patches/apply_add_rest_controller_and_highscore_scheduler_tail_coverage_tests.sh`
- `patches/apply_raise_jacoco_line_coverage_gate_to_92.sh`
- `coverage_patches_batch_49_manifest.md`

## Objetivo

Adicionar cobertura de baixo risco nas "caudas" restantes após o Batch 48:

- endpoints REST com branches de erro/delegação pouco exercitados;
- `HighscoreScrapeScheduler` em caminhos de falha e startup;
- gate de line coverage alinhado ao relatório atual.

## Testes adicionados

### RestControllerTailCoverageTest

Cobre:

- `CharacterController`
  - `getCharacter`;
  - `getCharacterNames`;
  - timeline/deaths com validação de character;
  - erro `404` quando character não existe;
  - erro `400` para range inválido;
  - online history/sessions/activity summary;
  - character highscores para `EXPERIENCE`;
  - character highscores para categoria não-EXP;
  - erro `400` quando world é obrigatório;
  - erro `400` quando query service rejeita filtros.

- `HighscoreController`
  - mapeamento de `IllegalArgumentException` para `400` nos endpoints dedicados:
    - `/exp/daily`;
    - `/exp/ranks`;
    - `/exp/gains`;
    - `/history`.

- Controllers simples:
  - `WorldController`;
  - `ScrapeJobController`;
  - `AdminScraperController.runCharacterDetails`.

### HighscoreSchedulerTailCoverageTest

Cobre:

- cron task de highscore que falha e marca o job como failure;
- startup plan executado em virtual thread e finalizado com sucesso;
- startup skip quando o scheduling global está desabilitado;
- startup skip para plano sem `runOnStartup`;
- log de configuração habilitada sem agendar execução.

## Gate de cobertura

O segundo patch sobe o gate de line coverage do JaCoCo de `91%` para `92%`.

O relatório após o Batch 48 + hotfix já mediu aproximadamente:

- `285` testes;
- `92%` instruction coverage;
- `70%` branch coverage;
- `92%` line coverage;
- `application.query` em aproximadamente `93%`.

## Validação recomendada

```bash
bash patches/apply_add_rest_controller_and_highscore_scheduler_tail_coverage_tests.sh
make test

bash patches/apply_raise_jacoco_line_coverage_gate_to_92.sh
make qa
make test-coverage
```

## Resultado esperado

- Testes: `285` -> aproximadamente `294`;
- Gate JaCoCo: `91%` -> `92%`;
- Ganho esperado em:
  - `infrastructure.adapter.web.rest`;
  - `application.scheduler`;
  - cobertura branch total.
