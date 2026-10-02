package org.aezden.backend.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired AppUserRepository users;

    record Login(String accessToken, Cookie refresh) {}

    private Login register(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"correct horse\",\"displayName\":\"Ada\"}".formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return login(r);
    }

    private static Login login(MvcResult r) throws Exception {
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        return new Login(token, r.getResponse().getCookie(SessionService.COOKIE));
    }

    private static String email() {
        return UUID.randomUUID() + "@example.com";
    }

    @Test
    void registerSetsHardenedRefreshCookie() throws Exception {
        String header = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"correct horse\",\"displayName\":\"Ada\"}".formatted(email())))
                .andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(header).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
    }

    @Test
    void apiRequiresBearerToken() throws Exception {
        Login a = register(email());
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + a.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Ada"));
    }

    @Test
    void accountsAreScopedToTheirOwner() throws Exception {
        Login a = register(email()), b = register(email());
        mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + a.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"AWS\",\"displayName\":\"aws-prod\",\"secretRef\":\"vault://x\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + a.accessToken()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + b.accessToken()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void refreshRotatesAndReuseRevokesTheFamily() throws Exception {
        Login first = register(email());
        Login second = login(mvc.perform(post("/api/v1/auth/refresh").cookie(first.refresh()))
                .andExpect(status().isOk()).andReturn());
        assertThat(second.refresh().getValue()).isNotEqualTo(first.refresh().getValue());

        // Replaying the rotated-out token is treated as theft: it fails, and so does the legit newer one.
        mvc.perform(post("/api/v1/auth/refresh").cookie(first.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(second.refresh())).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutKillsTheRefreshToken() throws Exception {
        Login a = register(email());
        mvc.perform(post("/api/v1/auth/logout").cookie(a.refresh())).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(a.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginAndRegisterFailures() throws Exception {
        String email = email();
        register(email);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"correct horse\"}".formatted(email.toUpperCase())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"wrong password\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"correct horse\",\"displayName\":\"Ada\"}".formatted(email)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providersListIsPublicAndEmptyWhenUnconfigured() throws Exception {
        mvc.perform(get("/api/v1/auth/providers")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void oauthLinksOnlyOnVerifiedEmail() throws Exception {
        String email = email();
        register(email);
        UUID passwordUser = users.findByEmail(email).orElseThrow().getId();

        AppUser unverified = authService.oauthLogin("github",
                Map.of("id", 1001, "login", "mallory", "email", email, "email_verified", false));
        assertThat(unverified.getId()).isNotEqualTo(passwordUser);
        assertThat(unverified.getEmail()).isNull();

        AppUser google = authService.oauthLogin("google",
                Map.of("sub", "g-42", "email", email.toUpperCase(), "email_verified", true, "name", "Ada"));
        assertThat(google.getId()).isEqualTo(passwordUser);

        AppUser again = authService.oauthLogin("google", Map.of("sub", "g-42", "email_verified", false));
        assertThat(again.getId()).isEqualTo(passwordUser);
    }
}
