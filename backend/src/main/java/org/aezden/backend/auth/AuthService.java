package org.aezden.backend.auth;

import lombok.RequiredArgsConstructor;
import org.aezden.backend.auth.AuthDtos.LoginRequest;
import org.aezden.backend.auth.AuthDtos.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    // Compared against when the email is unknown, so response time doesn't reveal which emails exist.
    private String dummyHash;

    @Transactional
    public AppUser register(RegisterRequest r) {
        if (r.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long");
        }
        String email = normalize(r.email());
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        return users.save(AppUser.builder().email(email).displayName(r.displayName().trim())
                .passwordHash(passwordEncoder.encode(r.password())).createdAt(Instant.now()).build());
    }

    public AppUser login(LoginRequest r) {
        Optional<AppUser> user = users.findByEmail(normalize(r.email()));
        String hash = user.map(AppUser::getPasswordHash).orElse(null);
        if (hash == null) {
            passwordEncoder.matches(r.password(), dummyHash());
        } else if (passwordEncoder.matches(r.password(), hash)) {
            return user.get();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    /**
     * Finds or creates the user behind an OAuth2 login. Order: same provider identity, then an existing
     * account with the same <em>verified</em> email (linking), else a new user. Unverified emails never link,
     * otherwise anyone could claim someone else's account by setting their address at a provider.
     */
    @Transactional
    public AppUser oauthLogin(String provider, Map<String, Object> a) {
        boolean google = "google".equals(provider);
        String subject = String.valueOf(a.get(google ? "sub" : "id"));
        String email = a.get("email") instanceof String e ? normalize(e) : null;
        boolean verified = Boolean.TRUE.equals(a.get("email_verified"));
        String name = firstNonBlank(a.get("name"), a.get("login"), email, "CloCost user");
        String avatar = (String) a.get(google ? "picture" : "avatar_url");

        AppUser user = (google ? users.findByGoogleId(subject) : users.findByGithubId(subject))
                .or(() -> verified && email != null ? users.findByEmail(email) : Optional.empty())
                .orElseGet(() -> AppUser.builder().displayName(name).createdAt(Instant.now())
                        .email(verified ? email : null).build());
        if (google) user.setGoogleId(subject); else user.setGithubId(subject);
        if (user.getAvatarUrl() == null) user.setAvatarUrl(avatar);
        return users.save(user);
    }

    public AppUser get(java.util.UUID id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String dummyHash() {
        if (dummyHash == null) dummyHash = passwordEncoder.encode("timing-equalizer");
        return dummyHash;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) if (v instanceof String s && !s.isBlank()) return s.length() > 100 ? s.substring(0, 100) : s;
        return null;
    }
}
