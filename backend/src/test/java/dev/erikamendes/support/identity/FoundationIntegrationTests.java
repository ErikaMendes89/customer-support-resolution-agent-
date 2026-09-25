package dev.erikamendes.support.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.auth.username=demo", "app.auth.password=test-password",
        "app.auth.secondary-username=demo-horizonte", "app.auth.secondary-password=test-password-secondary"})
@AutoConfigureMockMvc
class FoundationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void databaseHasPgvectorExtension() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class)).isEqualTo(1);
    }

    @Test
    void apiRequiresCredentialsAndReturnsCurrentUser() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").with(httpBasic("demo", "wrong")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.organizationName").value("Aurora Demo"));
        mvc.perform(get("/api/v1/me").with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("demo"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
