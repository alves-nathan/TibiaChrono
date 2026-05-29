#!/usr/bin/env bash
set -Eeuo pipefail

if [[ ! -f "pom.xml" || ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

if ! command -v git >/dev/null 2>&1; then
  echo "ERROR: git is required to apply this patch cleanly." >&2
  exit 1
fi

STAMP="$(date +%Y%m%d%H%M%S)"
BACKUP_DIR="patches/.backups/query-model-package-boundary-$STAMP"
mkdir -p "$BACKUP_DIR"

for f in 'src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java' 'src/main/java/com/nathan/tibiastats/application/service/HighscoreApiQueryService.java' 'src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java' 'src/main/java/com/nathan/tibiastats/application/service/WorldOnlineAnalyticsService.java' 'src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java' 'src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java' 'src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java' 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/graphql/StatsGraphQLController.java' 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java' 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java' 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/HighscoreController.java' 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/ScrapeJobController.java' 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldAnalyticsController.java' 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldController.java' 'src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java'; do
  if [[ -f "$f" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$f")"
    cp "$f" "$BACKUP_DIR/$f"
  fi
done

PATCH_FILE="$(mktemp)"
trap 'rm -f "$PATCH_FILE"' EXIT
cat > "$PATCH_FILE" <<'PATCH_EOF'
diff --git a/src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java b/src/main/java/com/nathan/tibiastats/application/query/ApiQueryService.java
similarity index 99%
rename from src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java
rename to src/main/java/com/nathan/tibiastats/application/query/ApiQueryService.java
index 24b6c21..ed120cd 100644
--- a/src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java
+++ b/src/main/java/com/nathan/tibiastats/application/query/ApiQueryService.java
@@ -1,10 +1,9 @@
-package com.nathan.tibiastats.application.service;
+package com.nathan.tibiastats.application.query;
 
 import com.nathan.tibiastats.domain.model.StatCategory;
 import org.springframework.jdbc.core.JdbcTemplate;
 import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
 import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
-import org.springframework.stereotype.Service;
 
 import java.sql.ResultSet;
 import java.sql.SQLException;
@@ -18,7 +17,7 @@ import java.util.Optional;
 import java.sql.Types;
 import java.time.ZoneOffset;
 
-@Service
+@ReadModelService
 public class ApiQueryService {
     private final NamedParameterJdbcTemplate jdbc;
 
diff --git a/src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java b/src/main/java/com/nathan/tibiastats/application/query/CharacterTimelineService.java
similarity index 99%
rename from src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java
rename to src/main/java/com/nathan/tibiastats/application/query/CharacterTimelineService.java
index 99fb363..e1ab90c 100644
--- a/src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java
+++ b/src/main/java/com/nathan/tibiastats/application/query/CharacterTimelineService.java
@@ -1,10 +1,9 @@
-package com.nathan.tibiastats.application.service;
+package com.nathan.tibiastats.application.query;
 
 import com.nathan.tibiastats.domain.model.StatCategory;
 import org.springframework.jdbc.core.JdbcTemplate;
 import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
 import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
-import org.springframework.stereotype.Service;
 
 import java.sql.ResultSet;
 import java.sql.SQLException;
@@ -17,7 +16,7 @@ import java.util.LinkedHashMap;
 import java.util.List;
 import java.util.Map;
 
-@Service
+@ReadModelService
 public class CharacterTimelineService {
     private static final int DEFAULT_LIMIT = 200;
     private static final int DEFAULT_MAX_GAP_MINUTES = 15;
diff --git a/src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java b/src/main/java/com/nathan/tibiastats/application/query/GuildQueryService.java
similarity index 98%
rename from src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java
rename to src/main/java/com/nathan/tibiastats/application/query/GuildQueryService.java
index 1c2039a..3d36e6d 100644
--- a/src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java
+++ b/src/main/java/com/nathan/tibiastats/application/query/GuildQueryService.java
@@ -1,16 +1,15 @@
-package com.nathan.tibiastats.application.service;
+package com.nathan.tibiastats.application.query;
 
 import com.nathan.tibiastats.domain.model.*;
 import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
 import com.nathan.tibiastats.infrastructure.persistence.SpringGuildRepository;
-import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 
 import java.time.Instant;
 import java.time.LocalDate;
 import java.util.List;
 
-@Service
+@ReadModelService
 public class GuildQueryService {
     private final SpringGuildRepository guilds;
     private final CharacterRepositoryPort characters;
diff --git a/src/main/java/com/nathan/tibiastats/application/service/HighscoreApiQueryService.java b/src/main/java/com/nathan/tibiastats/application/query/HighscoreApiQueryService.java
similarity index 99%
rename from src/main/java/com/nathan/tibiastats/application/service/HighscoreApiQueryService.java
rename to src/main/java/com/nathan/tibiastats/application/query/HighscoreApiQueryService.java
index 2950c54..379c62c 100644
--- a/src/main/java/com/nathan/tibiastats/application/service/HighscoreApiQueryService.java
+++ b/src/main/java/com/nathan/tibiastats/application/query/HighscoreApiQueryService.java
@@ -1,10 +1,9 @@
-package com.nathan.tibiastats.application.service;
+package com.nathan.tibiastats.application.query;
 
 import com.nathan.tibiastats.domain.model.StatCategory;
 import org.springframework.jdbc.core.JdbcTemplate;
 import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
 import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
-import org.springframework.stereotype.Service;
 
 import java.sql.ResultSet;
 import java.sql.SQLException;
@@ -13,7 +12,7 @@ import java.time.Instant;
 import java.time.LocalDate;
 import java.util.List;
 
-@Service
+@ReadModelService
 public class HighscoreApiQueryService {
     private final NamedParameterJdbcTemplate jdbc;
 
diff --git a/src/main/java/com/nathan/tibiastats/application/query/ReadModelService.java b/src/main/java/com/nathan/tibiastats/application/query/ReadModelService.java
new file mode 100644
index 0000000..ffc1505
--- /dev/null
+++ b/src/main/java/com/nathan/tibiastats/application/query/ReadModelService.java
@@ -0,0 +1,14 @@
+package com.nathan.tibiastats.application.query;
+
+import org.springframework.stereotype.Service;
+
+import java.lang.annotation.ElementType;
+import java.lang.annotation.Retention;
+import java.lang.annotation.RetentionPolicy;
+import java.lang.annotation.Target;
+
+@Target(ElementType.TYPE)
+@Retention(RetentionPolicy.RUNTIME)
+@Service
+public @interface ReadModelService {
+}
diff --git a/src/main/java/com/nathan/tibiastats/application/service/WorldOnlineAnalyticsService.java b/src/main/java/com/nathan/tibiastats/application/query/WorldOnlineAnalyticsService.java
similarity index 99%
rename from src/main/java/com/nathan/tibiastats/application/service/WorldOnlineAnalyticsService.java
rename to src/main/java/com/nathan/tibiastats/application/query/WorldOnlineAnalyticsService.java
index 13eb6db..30d8862 100644
--- a/src/main/java/com/nathan/tibiastats/application/service/WorldOnlineAnalyticsService.java
+++ b/src/main/java/com/nathan/tibiastats/application/query/WorldOnlineAnalyticsService.java
@@ -1,10 +1,9 @@
-package com.nathan.tibiastats.application.service;
+package com.nathan.tibiastats.application.query;
 
 import org.springframework.http.HttpStatus;
 import org.springframework.jdbc.core.JdbcTemplate;
 import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
 import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
-import org.springframework.stereotype.Service;
 import org.springframework.web.server.ResponseStatusException;
 
 import java.sql.ResultSet;
@@ -15,7 +14,7 @@ import java.util.Arrays;
 import java.util.List;
 import java.util.Locale;
 
-@Service
+@ReadModelService
 public class WorldOnlineAnalyticsService {
     private static final int DEFAULT_LIMIT = 50;
     private static final int MAX_LIMIT = 500;
diff --git a/src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java b/src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java
index 483c52a..addd21c 100644
--- a/src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java
+++ b/src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java
@@ -1,5 +1,6 @@
 package com.nathan.tibiastats.application.service;
 
+import com.nathan.tibiastats.application.query.ApiQueryService;
 import com.nathan.tibiastats.config.AppProperties;
 import com.nathan.tibiastats.config.GuildScrapeProperties;
 import com.nathan.tibiastats.config.HighscoreScrapeProperties;
diff --git a/src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java b/src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java
index 338bb6b..bc81e0d 100644
--- a/src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java
+++ b/src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java
@@ -1,5 +1,6 @@
 package com.nathan.tibiastats.application.service;
 
+import com.nathan.tibiastats.application.query.ApiQueryService;
 import org.springframework.stereotype.Service;
 
 import java.time.Instant;
diff --git a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/graphql/StatsGraphQLController.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/graphql/StatsGraphQLController.java
index 6c94790..c84d108 100644
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/graphql/StatsGraphQLController.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/graphql/StatsGraphQLController.java
@@ -1,7 +1,7 @@
 package com.nathan.tibiastats.infrastructure.adapter.web.graphql;
 
 import com.nathan.tibiastats.application.service.AnalyticsService;
-import com.nathan.tibiastats.application.service.ApiQueryService;
+import com.nathan.tibiastats.application.query.ApiQueryService;
 import com.nathan.tibiastats.domain.model.Scrape;
 import com.nathan.tibiastats.domain.model.StatCategory;
 import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
diff --git a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java
index b29c167..d4b91ac 100644
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java
@@ -1,9 +1,9 @@
 package com.nathan.tibiastats.infrastructure.adapter.web.rest;
 
-import com.nathan.tibiastats.application.service.ApiQueryService;
+import com.nathan.tibiastats.application.query.ApiQueryService;
 import com.nathan.tibiastats.application.service.CharacterOnlineActivityService;
-import com.nathan.tibiastats.application.service.CharacterTimelineService;
-import com.nathan.tibiastats.application.service.HighscoreApiQueryService;
+import com.nathan.tibiastats.application.query.CharacterTimelineService;
+import com.nathan.tibiastats.application.query.HighscoreApiQueryService;
 import com.nathan.tibiastats.domain.model.StatCategory;
 import org.springframework.format.annotation.DateTimeFormat;
 import org.springframework.http.HttpStatus;
diff --git a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java
index 93edc7e..fa17700 100644
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java
@@ -1,6 +1,6 @@
 package com.nathan.tibiastats.infrastructure.adapter.web.rest;
 
-import com.nathan.tibiastats.application.service.GuildQueryService;
+import com.nathan.tibiastats.application.query.GuildQueryService;
 import com.nathan.tibiastats.application.service.GuildScrapeService;
 import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
 import org.springframework.format.annotation.DateTimeFormat;
diff --git a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/HighscoreController.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/HighscoreController.java
index c666fa4..3b3cac5 100644
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/HighscoreController.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/HighscoreController.java
@@ -1,6 +1,6 @@
 package com.nathan.tibiastats.infrastructure.adapter.web.rest;
 
-import com.nathan.tibiastats.application.service.HighscoreApiQueryService;
+import com.nathan.tibiastats.application.query.HighscoreApiQueryService;
 import com.nathan.tibiastats.domain.model.StatCategory;
 import org.springframework.format.annotation.DateTimeFormat;
 import org.springframework.http.HttpStatus;
diff --git a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/ScrapeJobController.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/ScrapeJobController.java
index c5cec85..1f656a2 100644
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/ScrapeJobController.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/ScrapeJobController.java
@@ -1,6 +1,6 @@
 package com.nathan.tibiastats.infrastructure.adapter.web.rest;
 
-import com.nathan.tibiastats.application.service.ApiQueryService;
+import com.nathan.tibiastats.application.query.ApiQueryService;
 import org.springframework.web.bind.annotation.*;
 
 import java.util.List;
diff --git a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldAnalyticsController.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldAnalyticsController.java
index 6a14b9f..b70264f 100644
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldAnalyticsController.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldAnalyticsController.java
@@ -1,6 +1,6 @@
 package com.nathan.tibiastats.infrastructure.adapter.web.rest;
 
-import com.nathan.tibiastats.application.service.WorldOnlineAnalyticsService;
+import com.nathan.tibiastats.application.query.WorldOnlineAnalyticsService;
 import org.springframework.format.annotation.DateTimeFormat;
 import org.springframework.web.bind.annotation.GetMapping;
 import org.springframework.web.bind.annotation.PathVariable;
diff --git a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldController.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldController.java
index 502762f..1d9d9f8 100644
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldController.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldController.java
@@ -1,6 +1,6 @@
 package com.nathan.tibiastats.infrastructure.adapter.web.rest;
 
-import com.nathan.tibiastats.application.service.ApiQueryService;
+import com.nathan.tibiastats.application.query.ApiQueryService;
 import org.springframework.web.bind.annotation.*;
 
 import java.util.List;
diff --git a/src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java b/src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java
index fdb897b..7eb89bd 100644
--- a/src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java
+++ b/src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java
@@ -1,5 +1,6 @@
 package com.nathan.tibiastats.architecture;
 
+import com.nathan.tibiastats.application.query.ReadModelService;
 import com.tngtech.archunit.core.importer.ImportOption;
 import com.tngtech.archunit.junit.AnalyzeClasses;
 import com.tngtech.archunit.junit.ArchTest;
@@ -36,4 +37,19 @@ class ArchitectureRulesTest {
     static final ArchRule domain_ports_should_not_depend_on_infrastructure = noClasses()
             .that().resideInAPackage("..domain.port..")
             .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");
+
+    @ArchTest
+    static final ArchRule read_model_services_should_live_in_query_package = classes()
+            .that().areAnnotatedWith(ReadModelService.class)
+            .should().resideInAPackage("..application.query..");
+
+    @ArchTest
+    static final ArchRule write_application_services_should_not_depend_on_spring_jdbc = noClasses()
+            .that().resideInAPackage("..application.service..")
+            .should().dependOnClassesThat().resideInAnyPackage(
+                    "org.springframework.jdbc..",
+                    "org.springframework.jdbc.core..",
+                    "org.springframework.jdbc.core.namedparam.."
+            );
+
 }

PATCH_EOF

if git apply --check "$PATCH_FILE"; then
  git apply --whitespace=nowarn "$PATCH_FILE"
else
  echo "ERROR: patch does not apply cleanly. Backup was created at $BACKUP_DIR" >&2
  echo "Tip: verify that previous architecture patches were applied in order." >&2
  exit 1
fi

echo "Done. Read model package boundary and ArchUnit rules applied."
echo "Backup directory: $BACKUP_DIR"
echo "Next step: make qa"
