package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {
    Optional<BlacklistedToken> findByJti(String jti);
}