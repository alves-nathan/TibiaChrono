# TibiaChrono Architecture/Quality Patch Pack

Apply from the project root, in this order:

```bash
bash patches/apply_repository_hygiene_and_clean_export.sh
bash patches/apply_security_and_configuration_hardening.sh
bash patches/apply_quality_gates_archunit_spotless_jacoco.sh
bash patches/apply_database_legacy_documentation.sh
```

Recommended validation after the first two patches:

```bash
make test
```

Recommended validation after quality gates:

```bash
make qa
```

## Included patches

1. `apply_repository_hygiene_and_clean_export.sh`
   - Updates `.gitignore`.
   - Removes local/generated artifacts such as `.test-maven`, `target`, `.idea`, `.bak*`, and `Zone.Identifier` files.
   - Adds `scripts/export-clean.sh`.
   - Adds `make audit-worktree`, `make clean-local-artifacts`, and `make export-clean`.

2. `apply_security_and_configuration_hardening.sh`
   - Changes default `ddl-auto` to `validate`.
   - Fixes GraphQL path from `/graphql.` to `/graphql`.
   - Removes stale root-level `application.yml` after creating a backup.
   - Protects `/api/admin/**` with `ROLE_ADMIN`.
   - Prevents public registration from assigning elevated roles.
   - Adds role claims to access tokens and maps them back to Spring Security authorities.
   - Registers `TokenBlacklistFilter` in the security filter chain.

3. `apply_quality_gates_archunit_spotless_jacoco.sh`
   - Adds Maven Enforcer.
   - Adds Spotless.
   - Adds JaCoCo `verify` report/check with initial 15% line coverage threshold.
   - Adds ArchUnit tests for initial architecture boundaries.
   - Adds `make qa`, `make arch-test`, `make format`, and `make format-check`.

4. `apply_database_legacy_documentation.sh`
   - Adds `docs/architecture` documents.
   - Adds ADRs for Flyway validation, JDBC read models, and compact highscore storage.
   - Documents known database legacy/transitional areas.
