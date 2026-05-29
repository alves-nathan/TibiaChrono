package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.UserAccount;
import com.nathan.tibiastats.domain.port.UserAccountRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, UserAccountRepositoryPort {
    Optional<UserAccount> findByUsername(String username);
}