package dev.erikamendes.support.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.auth.username=demo", "app.auth.password=test-password",
        "app.auth.secondary-username=demo-horizonte", "app.auth.secondary-password=test-password-secondary"})
@AutoConfigureMockMvc
class CaseIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void casesAndEventsStayInsideTheirOrganization() throws Exception {
        String first = create("demo", "test-password", "Pagamento não encontrado");
        String second = create("demo-horizonte", "test-password-secondary", "Reserva não localizada");

        mvc.perform(get("/api/v1/cases/{id}", first).with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supportCase.title")
                        .value("Pagamento não encontrado"))
                .andExpect(jsonPath("$.events[0].eventType").value("CREATED"));
        mvc.perform(get("/api/v1/cases/{id}", first)
                        .with(httpBasic("demo-horizonte", "test-password-secondary")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cases/{id}", second).with(httpBasic("demo", "test-password")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/cases/{id}/status", first)
                        .with(httpBasic("demo-horizonte", "test-password-secondary")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isNotFound());

        String firstList = mvc.perform(get("/api/v1/cases").with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secondList = mvc.perform(get("/api/v1/cases")
                        .with(httpBasic("demo-horizonte", "test-password-secondary")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(firstList).contains(first).doesNotContain(second);
        assertThat(secondList).contains(second).doesNotContain(first);

        // The composite foreign key also prevents an event from being assigned to another tenant.
        Integer events = jdbc.queryForObject("SELECT count(*) FROM case_events WHERE case_id = ? AND organization_id = ?",
                Integer.class, UUID.fromString(first), UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(events).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO case_events "
                        + "(case_id, organization_id, event_type, to_status, actor) VALUES (?, ?, 'CREATED', 'OPEN', 'demo')",
                UUID.fromString(first), UUID.fromString("22222222-2222-2222-2222-222222222222")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusTransitionsAreAtomicAndLeaveAnAuditTrail() throws Exception {
        String id = create("demo", "test-password", "Pedido de alteração de reserva");
        mvc.perform(patch("/api/v1/cases/{id}/status", id)
                        .with(httpBasic("demo", "test-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"note\":\"Verificando pedido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportCase.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[1].fromStatus").value("OPEN"))
                .andExpect(jsonPath("$.events[1].note").value("Verificando pedido"));
        mvc.perform(patch("/api/v1/cases/{id}/status", id)
                        .with(httpBasic("demo", "test-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/cases/{id}", id).with(httpBasic("demo", "test-password")))
                .andExpect(jsonPath("$.events.length()").value(2));
    }

    @Test
    void invalidInputAndMissingCsrfDoNotCreateCases() throws Exception {
        mvc.perform(post("/api/v1/cases").with(httpBasic("demo", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Teste\",\"description\":\"Descrição válida para teste\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cases").with(httpBasic("demo", "test-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"a\",\"description\":\"curta\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/cases?size=51").with(httpBasic("demo", "test-password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void browserCookieAndHeaderAllowWriting() throws Exception {
        var response = mvc.perform(get("/api/v1/me/csrf").with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andReturn().getResponse();
        var cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        mvc.perform(post("/api/v1/cases").with(httpBasic("demo", "test-password"))
                        .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Solicitação via browser\",\"description\":\"Conteúdo válido para o teste de CSRF.\"}"))
                .andExpect(status().isCreated());
    }

    private String create(String username, String password, String title) throws Exception {
        var result = mvc.perform(post("/api/v1/cases").with(httpBasic(username, password)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"Solicitação de demonstração para os testes.\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
