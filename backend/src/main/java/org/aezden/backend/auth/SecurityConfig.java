package org.aezden.backend.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Configuration
public class SecurityConfig {

    /**
     * The REST API: stateless, Bearer JWT only. CSRF is off because nothing here authenticates with a cookie —
     * except the refresh cookie, which is SameSite=Strict and scoped to /api/v1/auth (see AUTH.md).
     */
    @Bean
    @Order(1)
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/providers").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * The OAuth2 redirect dance with Google/GitHub. It needs a short-lived HTTP session to hold the `state`
     * between the redirect out and the callback; on success we swap it for our own refresh cookie, drop the
     * session and send the browser home, where the SPA calls /auth/refresh. No token ever goes in a URL.
     */
    @Bean
    @Order(2)
    SecurityFilterChain oauth(HttpSecurity http, AuthService authService, SessionService sessions,
                              OAuth2UserService<OAuth2UserRequest, OAuth2User> userService) throws Exception {
        return http.securityMatcher("/oauth2/**", "/login/oauth2/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .oauth2Login(o -> o
                        .loginPage("/")
                        .userInfoEndpoint(u -> u.userService(userService))
                        .successHandler((req, res, auth) -> {
                            var token = (OAuth2AuthenticationToken) auth;
                            AppUser user = authService.oauthLogin(token.getAuthorizedClientRegistrationId(),
                                    token.getPrincipal().getAttributes());
                            res.addHeader(HttpHeaders.SET_COOKIE, sessions.cookie(sessions.start(user).refreshToken()).toString());
                            Optional.ofNullable(req.getSession(false)).ifPresent(s -> s.invalidate());
                            res.sendRedirect("/");
                        })
                        .failureHandler((req, res, e) -> {
                            log.warn("OAuth2 login failed: {}", e.getMessage());
                            Optional.ofNullable(req.getSession(false)).ifPresent(s -> s.invalidate());
                            res.sendRedirect("/?auth_error=" + URLEncoder.encode("Sign-in was cancelled or failed", StandardCharsets.UTF_8));
                        }))
                .build();
    }

    /** Only providers with a configured client id are registered, so the app boots without any of them. */
    @Bean
    ClientRegistrationRepository clientRegistrations(
            @Value("${app.oauth.google.client-id:}") String googleId, @Value("${app.oauth.google.client-secret:}") String googleSecret,
            @Value("${app.oauth.github.client-id:}") String githubId, @Value("${app.oauth.github.client-secret:}") String githubSecret) {
        List<ClientRegistration> list = new ArrayList<>();
        if (!googleId.isBlank()) list.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(googleId).clientSecret(googleSecret).build());
        if (!githubId.isBlank()) list.add(CommonOAuth2Provider.GITHUB.getBuilder("github")
                .clientId(githubId).clientSecret(githubSecret).scope("read:user", "user:email").build());
        return new Registrations(list);
    }

    // Unlike InMemoryClientRegistrationRepository, this accepts an empty list.
    record Registrations(List<ClientRegistration> all) implements ClientRegistrationRepository, Iterable<ClientRegistration> {
        public ClientRegistration findByRegistrationId(String id) {
            return all.stream().filter(r -> r.getRegistrationId().equals(id)).findFirst().orElse(null);
        }
        public Iterator<ClientRegistration> iterator() { return all.iterator(); }
    }

    /**
     * GitHub's /user only exposes the *public* email, and says nothing about verification. Ask /user/emails
     * for the primary verified one so it can safely link to an existing account. (Google is OIDC and already
     * returns email + email_verified, so it never goes through this service.)
     */
    @Bean
    OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        var delegate = new DefaultOAuth2UserService();
        var github = RestClient.create("https://api.github.com");
        return request -> {
            OAuth2User user = delegate.loadUser(request);
            if (!"github".equals(request.getClientRegistration().getRegistrationId())) return user;
            Map<String, Object> attrs = new HashMap<>(user.getAttributes());
            attrs.put("email_verified", false);
            try {
                List<Map<String, Object>> emails = github.get().uri("/user/emails")
                        .headers(h -> h.setBearerAuth(request.getAccessToken().getTokenValue()))
                        .retrieve().body(new ParameterizedTypeReference<>() {});
                Optional.ofNullable(emails).orElse(List.of()).stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                        .findFirst().ifPresent(e -> { attrs.put("email", e.get("email")); attrs.put("email_verified", true); });
            } catch (RuntimeException e) {
                log.warn("Could not read GitHub emails: {}", e.getMessage()); // sign in still works, just no linking
            }
            return new DefaultOAuth2User(user.getAuthorities(), attrs, "id");
        };
    }

    /** HS256 key from APP_JWT_SECRET (base64, >= 32 bytes). Without it, a random per-boot key is used for dev. */
    @Bean
    SecretKey jwtKey(@Value("${app.auth.jwt-secret:}") String secret) {
        byte[] bytes;
        if (secret.isBlank()) {
            log.warn("APP_JWT_SECRET is not set: using a random key. Access tokens die on restart (refresh still works).");
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
        } else {
            bytes = Base64.getDecoder().decode(secret);
            if (bytes.length < 32) throw new IllegalStateException("APP_JWT_SECRET must decode to at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, @Value("${app.auth.issuer:clocost}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // bcrypt, prefixed so it can be upgraded later
    }
}
