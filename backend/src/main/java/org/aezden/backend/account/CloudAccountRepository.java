package org.aezden.backend.account;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CloudAccountRepository extends JpaRepository<CloudAccount, UUID> {
    List<CloudAccount> findByStatus(AccountStatus status);
}
