# Coverage patches batch 45

## Conteúdo

- `patches/apply_add_scraper_support_and_guild_planner_coverage_tests.sh`
- `patches/apply_raise_jacoco_line_coverage_gate_to_88.sh`
- `coverage_patches_batch_45_manifest.md`

## Objetivo

Aproveitar pontos de baixo risco na cobertura atual sem depender de rede externa:

- `TibiaHighscoreHttpClient`
- `FormerCharacterNamePageParser`
- `GuildScrapeTargetPlanner`

## Testes adicionados

### TibiaHighscoreHttpClientCoverageTest

Cobre:

- montagem da URL de highscores sem realizar chamada HTTP;
- encoding de mundo com espaço usando `%20`;
- mapeamento de todas as categorias internas de highscore para os códigos usados pelo Tibia.com.

### FormerCharacterNamePageParserCoverageTest

Cobre:

- fallback para o nome solicitado quando a página não contém `Former Names:`;
- normalização de CSV de former names quando a linha existe na tabela do Tibia.

### GuildScrapeTargetPlannerCoverageTest

Cobre:

- busca de mundos sem limite;
- aplicação de limite positivo antes do filtro de nomes em branco;
- busca de guilds ativas para refresh de detalhes;
- filtro de nomes nulos/em branco.

## Gate de cobertura

O segundo patch sobe o gate de line coverage do JaCoCo de `87%` para `88%`.

O relatório do Batch 44 já mediu aproximadamente:

- `88%` instruction coverage;
- `64%` branch coverage;
- `88%` line coverage;
- `application.service` em aproximadamente `91%`;
- `infrastructure.adapter.scraper` em aproximadamente `80%`;
- `application.query` em aproximadamente `89%`;
- `infrastructure.persistence` em aproximadamente `89%`.

## Validação recomendada

```bash
bash patches/apply_add_scraper_support_and_guild_planner_coverage_tests.sh
make test

bash patches/apply_raise_jacoco_line_coverage_gate_to_88.sh
make qa
make test-coverage
```

## Resultado esperado

- Testes: `242` -> aproximadamente `249`.
- Gate JaCoCo: `87%` -> `88%`.
- Ganho esperado principalmente em:
  - `infrastructure.adapter.scraper`;
  - `application.service`.
