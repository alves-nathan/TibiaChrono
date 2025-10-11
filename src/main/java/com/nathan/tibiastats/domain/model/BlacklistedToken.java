package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "token_blacklist")
public class BlacklistedToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, unique=true)
    private String jti; // JWT ID
    @Column(nullable=false)
    private String token; // full token (optional, for audit)
    @Column(name="revoked_at", nullable=false)
    private Instant revokedAt = Instant.now();
    private String reason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}