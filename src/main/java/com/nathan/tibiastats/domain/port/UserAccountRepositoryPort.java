package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.UserAccount;
import java.util.Optional;

public interface UserAccountRepositoryPort {
    Optional<UserAccount> findByUsername(String username);

    UserAccount save(UserAccount userAccount);
}
