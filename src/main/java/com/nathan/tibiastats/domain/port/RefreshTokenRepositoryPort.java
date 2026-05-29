package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.RefreshToken;
import com.nathan.tibiastats.domain.model.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepositoryPort {
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserAndRevokedFalse(UserAccount user);

    void deleteByExpiresAtBefore(Instant instant);

    RefreshToken save(RefreshToken refreshToken);
}
