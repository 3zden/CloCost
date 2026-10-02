package org.aezden.backend.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aezden.backend.auth.AuthDtos.AuthResponse;
import org.aezden.backend.auth.AuthDtos.LoginRequest;
import org.aezden.backend.auth.AuthDtos.RegisterRequest;
import org.aezden.backend.auth.AuthDtos.UserView;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final SessionService sessions;
    private final ClientRegistrationRepository registrations;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return respond(HttpStatus.CREATED, sessions.start(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(HttpStatus.OK, sessions.start(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = SessionService.COOKIE, required = false) String raw) {
        try {
            return respond(HttpStatus.OK, sessions.refresh(raw));
        } catch (ResponseStatusException e) {
            // A dead refresh cookie is useless; drop it so the client stops sending it.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, sessions.clearedCookie().toString()).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = SessionService.COOKIE, required = false) String raw) {
        sessions.logout(raw);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.clearedCookie().toString()).build();
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return UserView.of(authService.get(UUID.fromString(jwt.getSubject())));
    }

    /** Which "Continue with …" buttons the login screen should show. */
    @GetMapping("/providers")
    @SuppressWarnings("unchecked")
    public List<String> providers() {
        return StreamSupport.stream(((Iterable<ClientRegistration>) registrations).spliterator(), false)
                .map(ClientRegistration::getRegistrationId).toList();
    }

    private ResponseEntity<AuthResponse> respond(HttpStatus status, SessionService.Session s) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, sessions.cookie(s.refreshToken()).toString())
                .body(s.body());
    }
}
