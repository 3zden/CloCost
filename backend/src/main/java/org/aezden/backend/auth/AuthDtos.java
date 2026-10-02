package org.aezden.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    // 72 is bcrypt's input limit (in bytes; AuthService re-checks the byte length).
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record UserView(UUID id, String email, String displayName, String avatarUrl) {
        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl());
        }
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserView user) {}
}
