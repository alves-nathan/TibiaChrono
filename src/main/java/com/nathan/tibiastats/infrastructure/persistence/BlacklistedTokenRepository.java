package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.BlacklistedToken;
import com.nathan.tibiastats.domain.port.BlacklistedTokenRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long>, BlacklistedTokenRepositoryPort {
    Optional<BlacklistedToken> findByJti(String jti);
}