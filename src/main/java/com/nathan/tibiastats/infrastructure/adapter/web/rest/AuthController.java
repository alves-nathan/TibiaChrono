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
import org.springframework.web.bind.annotation.*;

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

    public AuthController(AuthenticationManager authManager,
                          JwtService jwt,
                          UserAccountRepository users,
                          PasswordEncoder encoder,
                          TokenService tokens) {
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

        var rtOpt = tokens.refreshRepo.findByToken(req.refreshToken());
        if (rtOpt.isEmpty() || Boolean.TRUE.equals(rtOpt.get().getRevoked())) {
            return ResponseEntity.status(401).body(Map.of("error", "revoked"));
        }
        if (rtOpt.get().getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.status(401).body(Map.of("error", "expired"));
        }

        // rotate refresh: revoke old, issue new
        tokens.revokeRefreshToken(req.refreshToken());
        String newRefresh = tokens.issueRefreshToken(user.getUsername());
        String newAccess = jwt.generateAccessToken(user.getUsername());
        return ResponseEntity.ok(new AuthResponse(newAccess, newRefresh));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Jws<Claims> jws = jwt.parse(token);
                tokens.revokeAccessToken(jws.getBody().getId(), token, "logout");
            } catch (JwtException ignored) {
                // invalid token — ignore
            }
        }
        return ResponseEntity.ok().build();
    }
}
