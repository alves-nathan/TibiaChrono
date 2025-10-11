package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.JwtService;
import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class TokenService {
    private final JwtService jwtService;
    public final RefreshTokenRepository refreshRepo;
    private final BlacklistedTokenRepository blacklistRepo;
    private final UserAccountRepository users;

    public TokenService(JwtService jwt, RefreshTokenRepository r, BlacklistedTokenRepository b, UserAccountRepository u){
        this.jwtService = jwt; this.refreshRepo = r; this.blacklistRepo = b; this.users = u;
    }

    @Transactional
    public String issueRefreshToken(String username){
        var token = jwtService.generateRefreshToken(username);
        var claims = jwtService.parse(token).getBody();
        var user = users.findByUsername(username).orElseThrow();
        var rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(token);
        rt.setExpiresAt(claims.getExpiration().toInstant());
        refreshRepo.save(rt);
        return token;
    }

    @Transactional
    public void revokeAccessToken(String jti, String token, String reason){
        blacklistRepo.findByJti(jti).ifPresentOrElse(
                b -> {},
                () -> {
                    var b = new BlacklistedToken();
                    b.setJti(jti); b.setToken(token); b.setReason(reason);
                    blacklistRepo.save(b);
                }
        );
    }

    @Transactional
    public void revokeRefreshToken(String refresh){
        refreshRepo.findByToken(refresh).ifPresent(rt -> { rt.setRevoked(true); refreshRepo.save(rt); });
    }

    public boolean isBlacklisted(String jti){ return blacklistRepo.findByJti(jti).isPresent(); }
}