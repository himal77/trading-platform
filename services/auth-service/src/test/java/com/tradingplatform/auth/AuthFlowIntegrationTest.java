package com.tradingplatform.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against real Postgres and Redis. Mocking the datastore here would hide exactly
 * the failures that matter: migrations, constraints, and TTL behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void registerIssuesAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("register-flow@example.com", "correct-horse-battery")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = "duplicate@example.com";
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "correct-horse-battery")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "correct-horse-battery")))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("wrong-pass@example.com", "correct-horse-battery")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("wrong-pass@example.com", "not-the-right-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shortPasswordFailsValidation() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("short@example.com", "tooshort")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshRotatesTokenSoTheOldOneStopsWorking() throws Exception {
        MvcResult registered = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("rotation@example.com", "correct-horse-battery")))
                .andExpect(status().isCreated())
                .andReturn();

        String firstRefreshToken = registered.getResponse().getCookie("refresh_token").getValue();

        MvcResult refreshed = mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", firstRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String secondRefreshToken = refreshed.getResponse().getCookie("refresh_token").getValue();
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // Replaying the consumed token must fail — this is what makes theft detectable.
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", firstRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenImmediately() throws Exception {
        MvcResult registered = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("logout@example.com", "correct-horse-battery")))
                .andExpect(status().isCreated())
                .andReturn();

        String refreshToken = registered.getResponse().getCookie("refresh_token").getValue();

        mockMvc.perform(post("/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsProfileForIdentityForwardedByGateway() throws Exception {
        MvcResult registered = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("profile@example.com", "correct-horse-battery")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode token = objectMapper.readTree(registered.getResponse().getContentAsString());
        String userId = subjectOf(token.get("accessToken").asText());

        mockMvc.perform(get("/auth/me").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile@example.com"))
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private String body(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("email", email);
            put("password", password);
        }});
    }

    private String subjectOf(String jwt) throws Exception {
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(jwt.split("\\.")[1]));
        return objectMapper.readTree(payload).get("sub").asText();
    }
}
