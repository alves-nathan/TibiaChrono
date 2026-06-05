# Coverage patches batch 50

## Conteúdo

- `patches/apply_add_guild_repository_config_and_highscore_category_tail_coverage_tests.sh`
- `patches/apply_raise_jacoco_line_coverage_gate_to_93.sh`
- `coverage_patches_batch_50_manifest.md`

## Objetivo

Adicionar cobertura de baixo risco após o Batch 49, sem alterar lógica de produção.

O foco do batch é cobrir linhas simples ainda descobertas:

- `CharacterTimelineHighscoreReadModelService.categoryName(...)`;
- métodos delegadores de `SpringGuildRepository`;
- propriedades legadas de `AppProperties.Highscores`;
- helpers de encoding dos HTTP clients de scraper.

## Testes adicionados

### CharacterTimelineHighscoreCategoryCoverageTest

Cobre:

- todos os códigos Tibia de categoria highscore `1..17`;
- fallback `UNKNOWN`.

### SpringGuildRepositoryTailCoverageTest

Cobre:

- normalização de nome de guild;
- fallback `findByNameIgnoreCase`;
- `saveGuild`;
- `findGuilds`;
- `findActiveForDetailsRefresh` com clamp de limite;
- `saveSnapshot`;
- `saveMembership`;
- `saveAndFlushMembership`;
- `flushMemberships`;
- `findActiveMemberships`;
- `findActiveMembershipForCharacter`;
- `findMemberships` por `Guild` e por `guildId`;
- `findMembershipHistory`;
- `saveEvent`;
- `findEvents` com clamp de limite;
- `saveInvite`;
- `findActiveInvite`;
- `findActiveInvites`.

### AppPropertiesCoverageTest

Cobre:

- root getters/setters;
- `Worlds`;
- `Highscores`;
- `CharacterDetails`;
- todos os getters/setters legados de `Highscores`.

### TibiaHttpClientEncodingCoverageTest

Cobre:

- encoding nulo e com espaço em `TibiaWorldHttpClient`;
- encoding nulo e com espaço em `TibiaGuildHttpClient`;
- rejeição de nome nulo em `TibiaCharacterHttpClient` antes de abrir conexão externa.

## Gate de cobertura

O segundo patch sobe o gate de line coverage do JaCoCo de `92%` para `93%`.

O relatório após o Batch 49 já mediu aproximadamente:

- `294` testes;
- `92%` instruction coverage;
- `70%` branch coverage;
- `92%` line coverage;
- `infrastructure.adapter.web.rest` em aproximadamente `95%`;
- `application.scheduler` em aproximadamente `92%`.

## Validação recomendada

```bash
bash patches/apply_add_guild_repository_config_and_highscore_category_tail_coverage_tests.sh
make test

bash patches/apply_raise_jacoco_line_coverage_gate_to_93.sh
make qa
make test-coverage
```

## Resultado esperado

- Testes: `294` -> aproximadamente `302`;
- Gate JaCoCo: `92%` -> `93%`;
- Ganho esperado em:
  - `application.query`;
  - `infrastructure.persistence`;
  - `config`;
  - `infrastructure.adapter.scraper`.
