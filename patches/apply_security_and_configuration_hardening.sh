#!/usr/bin/env bash
set -Eeuo pipefail

BACKUP_SUFFIX=".bak-security-and-config-hardening-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

required_files=(
  "src/main/resources/application.yml"
  "src/main/java/com/nathan/tibiastats/config/SecurityConfig.java"
  "src/main/java/com/nathan/tibiastats/config/JwtService.java"
  "src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/AuthController.java"
  "src/test/java/com/nathan/tibiastats/auth/AuthIntegrationTest.java"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "ERROR: required file not found: $file" >&2
    exit 1
  fi
  cp "$file" "$file$BACKUP_SUFFIX"
done

if [[ -f "application.yml" ]]; then
  cp "application.yml" "application.yml$BACKUP_SUFFIX"
fi

python3 - <<'PY'
from pathlib import Path
import re

# Safe defaults in the canonical application configuration.
app_yml = Path('src/main/resources/application.yml')
text = app_yml.read_text()
text = re.sub(r'(ddl-auto:\s*)update\b', r'\1validate', text)
text = text.replace('path: /graphql.', 'path: /graphql')
app_yml.write_text(text)

# The root-level application.yml is a stale duplicate. Keep a timestamped backup created by bash, then remove it.
root_yml = Path('application.yml')
if root_yml.exists():
    root_yml.unlink()

Path('src/main/java/com/nathan/tibiastats/config/SecurityConfig.java').write_text('''package com.nathan.tibiastats.config;

import com.nathan.tibiastats.application.service.TokenService;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, TokenBlacklistFilter tokenBlacklistFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/actuator/**", "/auth/login", "/auth/refresh", "/auth/register").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**", "/graphql").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterBefore(tokenBlacklistFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return authenticationConverter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.secret-key}") String secretKey
    ) {
        return NimbusJwtDecoder
                .withSecretKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public TokenBlacklistFilter tokenBlacklistFilter(TokenService tokens, JwtService jwt) {
        return new TokenBlacklistFilter(tokens, jwt);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
''')

Path('src/main/java/com/nathan/tibiastats/config/JwtService.java').write_text('''package com.nathan.tibiastats.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtService(
            @Value("${spring.security.oauth2.resourceserver.jwt.secret-key}") String secret,
            @Value("${tibiastats.jwt.access-ttl-ms:900000}") long accessTtlMs,
            @Value("${tibiastats.jwt.refresh-ttl-ms:1209600000}") long refreshTtlMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    public String generateAccessToken(String username) {
        return generateAccessToken(username, "USER");
    }

    public String generateAccessToken(String username, String roles) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(accessTtlMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("roles", normalizeRoles(roles))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(refreshTtlMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("type", "refresh")
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
        } catch (JwtException e) {
            throw e;
        }
    }

    private List<String> normalizeRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of("USER");
        }

        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
''')

Path('src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/AuthController.java').write_text('''package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.TokenService;
import com.nathan.tibiastats.config.JwtService;
import com.nathan.tibiastats.domain.model.UserAccount;
import com.nathan.tibiastats.infrastructure.persistence.UserAccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

record AuthRequest(String username, String password) {}
record AuthResponse(String accessToken, String refreshToken) {}
record RegisterRequest(String username, String password, String roles) {}
record RefreshRequest(String refreshToken) {}

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public AuthController(
            AuthenticationManager authManager,
            JwtService jwt,
            UserAccountRepository users,
            PasswordEncoder encoder,
            TokenService tokens
    ) {
        this.authManager = authManager;
        this.jwt = jwt;
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        users.findByUsername(req.username())
                .ifPresent(u -> { throw new IllegalArgumentException("username already exists"); });

        var acc = new UserAccount();
        acc.setUsername(req.username());
        acc.setPassword(encoder.encode(req.password()));
        acc.setRoles("USER");
        users.save(acc);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );

        UserAccount user = users.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found"));

        String access = jwt.generateAccessToken(auth.getName(), user.getRoles());
        String refresh = tokens.issueRefreshToken(auth.getName());

        return ResponseEntity.ok(new AuthResponse(access, refresh));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest req) {
        Jws<Claims> jws;
        try {
            jws = jwt.parse(req.refreshToken());
        } catch (JwtException ex) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid refresh token"));
        }

        Claims claims = jws.getPayload();
        if (!"refresh".equals(claims.get("type"))) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid refresh token"));
        }

        var opt = users.findByUsername(claims.getSubject());
        if (opt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        UserAccount user = opt.get();

        var rtOpt = tokens.findRefreshToken(req.refreshToken());
        if (rtOpt.isEmpty() || Boolean.TRUE.equals(rtOpt.get().getRevoked())) {
            return ResponseEntity.status(401).body(Map.of("error", "revoked"));
        }
        if (rtOpt.get().getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.status(401).body(Map.of("error", "expired"));
        }

        tokens.revokeRefreshToken(req.refreshToken());
        String newRefresh = tokens.issueRefreshToken(user.getUsername());
        String newAccess = jwt.generateAccessToken(user.getUsername(), user.getRoles());

        return ResponseEntity.ok(new AuthResponse(newAccess, newRefresh));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Jws<Claims> jws = jwt.parse(token);
                tokens.revokeAccessToken(jws.getPayload().getId(), token, "logout");
            } catch (JwtException ignored) {
                // Invalid token: logout is idempotent.
            }
        }
        return ResponseEntity.ok().build();
    }
}
''')

Path('src/test/java/com/nathan/tibiastats/auth/AuthIntegrationTest.java').write_text('''package com.nathan.tibiastats.auth;

import com.nathan.tibiastats.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractPostgresTest {
    @Autowired MockMvc mvc;

    @Test
    void register_login_refresh_logout_blacklist() throws Exception {
        // Register. Client-provided roles must be ignored by the public endpoint.
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"secret\",\"roles\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        // Login → tokens
        var login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyString())))
                .andReturn();

        String access = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        String refresh = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.refreshToken");

        // Public registration cannot escalate privileges to ADMIN.
        mvc.perform(get("/api/admin/scrapers/status").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());

        // Refresh → new pair
        var refreshed = mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\""+refresh+"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyString())))
                .andReturn();

        String access2 = com.jayway.jsonpath.JsonPath.read(refreshed.getResponse().getContentAsString(), "$.accessToken");

        // Logout (blacklist access2)
        mvc.perform(post("/auth/logout").header("Authorization","Bearer "+access2))
                .andExpect(status().isOk());
    }
}
''')
PY

cat <<'MSG'

Done.
Security/configuration hardening applied.

Changes:
  - default ddl-auto is now validate
  - GraphQL path is /graphql
  - stale root application.yml was removed after backup
  - /api/admin/** now requires ROLE_ADMIN
  - public registration always creates USER accounts
  - access tokens now carry roles and SecurityConfig reads them
  - TokenBlacklistFilter is now registered in the filter chain

Next steps:
  make test
MSG
