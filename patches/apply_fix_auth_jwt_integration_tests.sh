#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(pwd)}"
cd "$ROOT"

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java" ]; then
  echo "Run this script from the TibiaChrono project root, or pass the project path as the first argument." >&2
  exit 1
fi

mkdir -p src/main/java/com/nathan/tibiastats/config
mkdir -p src/main/java/com/nathan/tibiastats/application/service
mkdir -p src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest

cat > src/main/java/com/nathan/tibiastats/config/JwtService.java <<'JAVA'
package com.nathan.tibiastats.config;

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
import java.util.Date;
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
        Instant now = Instant.now();
        Instant exp = now.plusMillis(accessTtlMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
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
}
JAVA

cat > src/main/java/com/nathan/tibiastats/config/SecurityConfig.java <<'JAVA'
package com.nathan.tibiastats.config;

import com.nathan.tibiastats.application.service.TokenService;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/actuator/**", "/auth/login", "/auth/refresh", "/auth/register").permitAll()
                        .requestMatchers("/api/**", "/graphql").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

        return http.build();
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
JAVA

cat > src/main/java/com/nathan/tibiastats/application/service/TokenService.java <<'JAVA'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.JwtService;
import com.nathan.tibiastats.domain.model.BlacklistedToken;
import com.nathan.tibiastats.domain.model.RefreshToken;
import com.nathan.tibiastats.infrastructure.persistence.BlacklistedTokenRepository;
import com.nathan.tibiastats.infrastructure.persistence.RefreshTokenRepository;
import com.nathan.tibiastats.infrastructure.persistence.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class TokenService {
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshRepo;
    private final BlacklistedTokenRepository blacklistRepo;
    private final UserAccountRepository users;

    public TokenService(
            JwtService jwtService,
            RefreshTokenRepository refreshRepo,
            BlacklistedTokenRepository blacklistRepo,
            UserAccountRepository users
    ) {
        this.jwtService = jwtService;
        this.refreshRepo = refreshRepo;
        this.blacklistRepo = blacklistRepo;
        this.users = users;
    }

    @Transactional
    public String issueRefreshToken(String username) {
        var token = jwtService.generateRefreshToken(username);
        var claims = jwtService.parse(token).getPayload();
        var user = users.findByUsername(username).orElseThrow();

        var rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(token);
        rt.setExpiresAt(claims.getExpiration().toInstant());
        refreshRepo.save(rt);

        return token;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findRefreshToken(String refreshToken) {
        return refreshRepo.findByToken(refreshToken);
    }

    @Transactional
    public void revokeAccessToken(String jti, String token, String reason) {
        blacklistRepo.findByJti(jti).ifPresentOrElse(
                existing -> { },
                () -> {
                    var blacklistedToken = new BlacklistedToken();
                    blacklistedToken.setJti(jti);
                    blacklistedToken.setToken(token);
                    blacklistedToken.setReason(reason);
                    blacklistRepo.save(blacklistedToken);
                }
        );
    }

    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        refreshRepo.findByToken(refreshToken).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshRepo.save(rt);
        });
    }

    @Transactional(readOnly = true)
    public boolean isBlacklisted(String jti) {
        return blacklistRepo.findByJti(jti).isPresent();
    }
}
JAVA

cat > src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/AuthController.java <<'JAVA'
package com.nathan.tibiastats.infrastructure.adapter.web.rest;

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
        acc.setRoles((req.roles() == null || req.roles().isBlank()) ? "USER" : req.roles());
        users.save(acc);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );

        String access = jwt.generateAccessToken(auth.getName());
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
        String newAccess = jwt.generateAccessToken(user.getUsername());

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
JAVA

echo "Applied JWT/auth integration-test fixes. Now run: ./run-tests.sh"
