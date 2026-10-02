package org.aezden.backend.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

// "user" is reserved in PostgreSQL, hence app_user.
@Entity
@Table(name = "app_user")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    // Null only for an OAuth user whose provider gave us no verified email.
    @Column(unique = true)
    private String email;
    // Null for OAuth-only users: they cannot sign in with a password until they set one.
    private String passwordHash;
    @Column(nullable = false, length = 100)
    private String displayName;
    private String avatarUrl;
    // ponytail: one column per provider; move to a user_identity table if a third provider shows up
    @Column(unique = true)
    private String googleId;
    @Column(unique = true)
    private String githubId;
    @Column(nullable = false)
    private Instant createdAt;
}
