# Legacy Management

TibiaChrono is evolving quickly through patch scripts. Legacy needs to be managed deliberately so fixes do not accumulate as hidden architecture debt.

## Patch policy

- Never edit already-applied Flyway migrations to fix production-like databases.
- Add new migrations for schema corrections.
- Keep patch scripts idempotent when possible.
- Every patch should print clear next steps.
- Prefer small, testable refactors over broad rewrites.

## Backup policy

Patch scripts may create timestamped `.bak-*` files for safety. These files are local artifacts and should not be committed.

## Deprecation policy

When a table, endpoint or service path becomes legacy:

1. Document why it is legacy.
2. Document the replacement.
3. Add tests around the replacement before removing the legacy path.
4. Remove legacy code only after consumers are migrated.
