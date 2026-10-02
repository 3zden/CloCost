package org.aezden.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.oauth.google.client-id=test-id", "app.oauth.google.client-secret=test-secret"})
@AutoConfigureMockMvc
class OAuthRedirectTest {
    @Autowired MockMvc mvc;

    @Test
    void startsGoogleAuthorizationWithCallbackOnTheSameOrigin() throws Exception {
        mvc.perform(get("/api/v1/auth/providers")).andExpect(jsonPath("$[0]").value("google"));
        String location = mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getHeader("Location");
        assertThat(location).startsWith("https://accounts.google.com/")
                .contains("client_id=test-id", "state=", "redirect_uri=http://localhost/login/oauth2/code/google");
    }

    @Test
    void unknownProviderDoesNotStartALogin() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/github")).andReturn().getResponse().getHeader("Location");
        assertThat(location == null || !location.contains("github.com")).isTrue();
    }
}
