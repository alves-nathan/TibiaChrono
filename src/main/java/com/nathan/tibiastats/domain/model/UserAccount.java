package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "users")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(nullable = false)
    private String password; // bcrypt hash
    @Column(nullable = false)
    private String roles = "USER"; // comma-separated
    @Column(nullable = false)
    private Boolean enabled = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String u) {
        this.username = u;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String p) {
        this.password = p;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String r) {
        this.roles = r;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean e) {
        this.enabled = e;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant t) {
        this.createdAt = t;
    }
}