package com.nathan.tibiastats.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final Key key;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtService(
            @Value("${spring.security.oauth2.resourceserver.jwt.secret-key}") String secret,
            @Value("${tibiastats.jwt.access-ttl-ms:900000}") long accessTtlMs,
            @Value("${tibiastats.jwt.refresh-ttl-ms:1209600000}") long refreshTtlMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    public String generateAccessToken(String username) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(accessTtlMs);
        return Jwts.builder().id(UUID.randomUUID().toString())             // jti
                .subject(username)                               // sub
                .issuedAt(Date.from(now))                        // iat
                .expiration(Date.from(exp))                      // exp
                .signWith(key)                                   // new style: no need for algorithm param
                .compact();
    }

    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(refreshTtlMs);
        return Jwts.builder().id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("type", "refresh")
                .signWith(key)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith((SecretKey) key)    // replaces deprecated setSigningKey
                    .build()
                    .parseSignedClaims(token);  // replaces parseClaimsJws
        } catch (JwtException e) {
            throw e;
        }
    }
}
