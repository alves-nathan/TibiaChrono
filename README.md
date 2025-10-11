# TibiaStats
### Dev (hot reload)
```bash
docker compose -f docker-compose.dev.yml up --build
```

App runs on http://localhost:8080 with DevTools reload. DB on localhost:5432.

### Prod-like
```
docker compose up --build -d
```

### REST examples

- GET `/api/online/total → { "total": 12345 }`
- GET `/api/online/worlds → list of worlds with counts`
- GET `/api/online/worlds/Antica → current count`
- GET `/api/online/worlds/Antica/history?from=...&to=...`
- GET `/api/character/YourName/stats?category=EXPERIENCE`

### GraphQL
POST to `/graphql` with:
```
{"query":"{ onlineTotal }"}
```

### Security
APIs require JWT Bearer token (resource server secret key configured). For local testing you can disable auth
temporarily in SecurityConfig or use a test JWT that matches the secret.
### Notes
Jsoup selectors are placeholders; inspect Tibia HTML and adjust.
Add Flyway migrations if you want to enforce schema instead of ddl-auto .
Extend Character fields by scraping per-character pages if needed.
