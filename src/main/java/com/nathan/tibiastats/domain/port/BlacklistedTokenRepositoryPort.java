package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.BlacklistedToken;
import java.util.Optional;

public interface BlacklistedTokenRepositoryPort {
    Optional<BlacklistedToken> findByJti(String jti);

    BlacklistedToken save(BlacklistedToken blacklistedToken);
}
