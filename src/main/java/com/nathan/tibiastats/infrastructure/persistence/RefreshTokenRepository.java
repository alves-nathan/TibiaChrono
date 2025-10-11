package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.RefreshToken;
import com.nathan.tibiastats.domain.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUserAndRevokedFalse(UserAccount user);
    void deleteByExpiresAtBefore(Instant instant);
}