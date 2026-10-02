package org.aezden.backend.account;

import jakarta.persistence.*;
import lombok.*;
import org.aezden.backend.cost.Provider;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cloud_account")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudAccount {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    // The AppUser who connected it. Nullable only for rows created before auth existed.
    private UUID ownerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Provider provider;
    @Column(nullable = false, length = 100)
    private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AccountStatus status;
    @Column(nullable = false)
    private String secretRef;
    private Instant connectedAt;
    @Column(nullable = false)
    private Instant createdAt;
}
