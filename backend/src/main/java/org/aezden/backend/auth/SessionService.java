package org.aezden.backend.auth;

import lombok.RequiredArgsConstructor;
import org.aezden.backend.auth.AuthDtos.AuthResponse;
import org.aezden.backend.auth.AuthDtos.UserView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Short-lived JWT access tokens + rotating opaque refresh tokens in an HttpOnly cookie.
 * See backend/AUTH.md for why, and for the alternatives.
 */
@Service
@RequiredArgsConstructor
public class SessionService {
    public static final String COOKIE = "clocost_refresh";
    // The browser only sends the cookie to the auth endpoints, never to the rest of the API.
    static final String COOKIE_PATH = "/api/v1/auth";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository tokens;
    private final AuthService authService;

    @Value("${app.auth.issuer:clocost}")
    private String issuer;
    @Value("${app.auth.access-token-ttl:15m}")
    private Duration accessTtl;
    @Value("${app.auth.refresh-token-ttl:14d}")
    private Duration refreshTtl;
    @Value("${app.auth.cookie-secure:true}")
    private boolean cookieSecure;

    /** The response body plus the raw refresh token the controller puts in the cookie. */
    public record Session(AuthResponse body, String refreshToken) {}

    /** Starts a new login: a fresh token family. */
    @Transactional
    public Session start(AppUser user) {
        return issue(user, UUID.randomUUID());
    }

    /**
     * Rotates: the presented token is revoked and a new one issued in the same family. Presenting a token that
     * was already rotated means it leaked (or was replayed), so the whole family is revoked and everyone holding
     * it — attacker and user — must sign in again.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class) // keep the family revocation when we throw
    public Session refresh(String raw) {
        if (raw == null || raw.isBlank()) throw unauthorized();
        RefreshToken token = tokens.findByTokenHash(hash(raw)).orElseThrow(SessionService::unauthorized);
        Instant now = Instant.now();
        // Conditional update makes this atomic: of two concurrent refreshes with one token, exactly one wins.
        if (tokens.revokeIfActive(token.getId(), now) == 0) {
            tokens.revokeFamily(token.getFamilyId(), now);
            throw unauthorized();
        }
        if (token.getExpiresAt().isBefore(now)) throw unauthorized();
        return issue(authService.get(token.getUserId()), token.getFamilyId());
    }

    /** Ends this device's session. The access token stays valid until it expires (at most accessTtl). */
    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) return;
        tokens.findByTokenHash(hash(raw)).ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    public ResponseCookie cookie(String raw) {
        return baseCookie(raw).maxAge(refreshTtl).build();
    }

    public ResponseCookie clearedCookie() {
        return baseCookie("").maxAge(0).build();
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        tokens.deleteExpired(Instant.now());
    }

    private Session issue(AppUser user, UUID familyId) {
        Instant now = Instant.now();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(RefreshToken.builder().tokenHash(hash(raw)).userId(user.getId()).familyId(familyId)
                .createdAt(now).expiresAt(now.plus(refreshTtl)).build());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plus(accessTtl)).claim("name", user.getDisplayName());
        if (user.getEmail() != null) claims.claim("email", user.getEmail());
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();

        return new Session(new AuthResponse(jwt, "Bearer", accessTtl.toSeconds(), UserView.of(user)), raw);
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(cookieSecure)
                .sameSite("Strict").path(COOKIE_PATH);
    }

    // Refresh tokens are 256 random bits, so a fast unsalted hash is enough: there is nothing to brute-force.
    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
    }
}
