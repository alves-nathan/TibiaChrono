package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.JwtService;
import com.nathan.tibiastats.domain.model.BlacklistedToken;
import com.nathan.tibiastats.domain.model.RefreshToken;
import com.nathan.tibiastats.domain.port.BlacklistedTokenRepositoryPort;
import com.nathan.tibiastats.domain.port.RefreshTokenRepositoryPort;
import com.nathan.tibiastats.domain.port.UserAccountRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class TokenService {
    private final JwtService jwtService;
    private final RefreshTokenRepositoryPort refreshRepo;
    private final BlacklistedTokenRepositoryPort blacklistRepo;
    private final UserAccountRepositoryPort users;

    public TokenService(
            JwtService jwtService,
            RefreshTokenRepositoryPort refreshRepo,
            BlacklistedTokenRepositoryPort blacklistRepo,
            UserAccountRepositoryPort users
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
