package org.aezden.backend.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

// One row per issued refresh token. Only the SHA-256 hash is stored, never the token itself.
// All tokens rotated out of the same login share a familyId, so reuse of an old one can revoke the whole chain.
@Entity
@Table(name = "refresh_token", indexes = @Index(columnList = "familyId"))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private UUID familyId;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant revokedAt;
}
