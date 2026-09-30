package org.aezden.backend.account;

import lombok.RequiredArgsConstructor;
import org.aezden.backend.cost.Provider;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final CloudAccountRepository repository;

    public CloudAccount connect(Provider provider, String displayName, String secretRef) {
        if (secretRef == null || secretRef.isBlank() || secretRef.length() > 255) {
            throw new IllegalArgumentException("secretRef must be a non-empty secret-store reference");
        }
        return repository.save(CloudAccount.builder().provider(provider).displayName(displayName)
                .status(AccountStatus.CONNECTED).secretRef(secretRef)
                .connectedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    public List<CloudAccount> list() {
        return repository.findAll();
    }
}
