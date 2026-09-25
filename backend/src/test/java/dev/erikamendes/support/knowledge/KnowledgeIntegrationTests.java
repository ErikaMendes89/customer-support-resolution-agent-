package dev.erikamendes.support.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.erikamendes.support.knowledge.application.EmbeddingClient;
import dev.erikamendes.support.knowledge.application.EmbeddingUnavailableException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.auth.username=demo", "app.auth.password=test-password",
        "app.auth.secondary-username=demo-horizonte", "app.auth.secondary-password=test-password-secondary",
        "app.embeddings.provider=fake"})
@AutoConfigureMockMvc
class KnowledgeIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ingestionIsIdempotentAndSearchReturnsTenantScopedSources() throws Exception {
        String payment = ingest("demo", "test-password", "Política de pagamento",
                "Pagamento antecipado é permitido para todas as reservas do hotel.", 201);
        String duplicate = ingest("demo", "test-password", "Cópia de pagamento",
                "Pagamento antecipado é permitido para todas as reservas do hotel.", 200);
        assertThat(duplicate).isEqualTo(payment);
        String other = ingest("demo-horizonte", "test-password-secondary", "Política da Horizonte",
                "Pagamento da organização Horizonte é feito exclusivamente no balcão.", 201);

        mvc.perform(get("/api/v1/documents/{id}", payment).with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chunks[0].ordinal").value(0));
        mvc.perform(get("/api/v1/documents/{id}", other).with(httpBasic("demo", "test-password")))
                .andExpect(status().isNotFound());
        Cookie csrf = csrfCookie("demo", "test-password");
        mvc.perform(delete("/api/v1/documents/{id}", other).with(httpBasic("demo", "test-password"))
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNotFound());

        String list = mvc.perform(get("/api/v1/documents").with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains(payment).doesNotContain(other);

        String hits = search("demo", "test-password", "pagamento", 5);
        assertThat(hits).contains(payment).doesNotContain(other);
        mvc.perform(get("/api/v1/documents/{id}", other)
                        .with(httpBasic("demo-horizonte", "test-password-secondary")))
                .andExpect(status().isOk());
    }

    @Test
    void rankingUsesVectorDistanceAndDeletionRemovesChunks() throws Exception {
        String payment = ingest("demo", "test-password", "Manual pagamento",
                "Pagamento confirmado após conferência do comprovante do cliente.", 201);
        ingest("demo", "test-password", "Manual reserva",
                "Reserva confirmada após disponibilidade e identificação do cliente.", 201);
        String hits = search("demo", "test-password", "pagamento", 1);
        assertThat(JsonPath.<String>read(hits, "$[0].documentId")).isEqualTo(payment);
        assertThat(JsonPath.<Double>read(hits, "$[0].similarity")).isGreaterThan(0.8);

        Cookie csrf = csrfCookie("demo", "test-password");
        mvc.perform(delete("/api/v1/documents/{id}", payment).with(httpBasic("demo", "test-password"))
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/documents/{id}", payment).with(httpBasic("demo", "test-password")))
                .andExpect(status().isNotFound());
        Long count = jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id = ?",
                Long.class, java.util.UUID.fromString(payment));
        assertThat(count).isZero();
    }

    @Test
    void failedEmbeddingLeavesNoDocument() throws Exception {
        Cookie csrf = csrfCookie("demo", "test-password");
        String title = "Falha vetorial de teste";
        mvc.perform(post("/api/v1/documents").with(httpBasic("demo", "test-password"))
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(title, "FALHA-EMBEDDING: documento que não deve ser salvo.")))
                .andExpect(status().isServiceUnavailable());
        String list = mvc.perform(get("/api/v1/documents").with(httpBasic("demo", "test-password")))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(title);
    }

    private String ingest(String user, String password, String title, String content, int expected) throws Exception {
        Cookie cookie = csrfCookie(user, password);
        var response = mvc.perform(post("/api/v1/documents").with(httpBasic(user, password))
                        .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(json(title, content)))
                .andExpect(status().is(expected)).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.id");
    }

    private String search(String user, String password, String query, int topK) throws Exception {
        Cookie cookie = csrfCookie(user, password);
        return mvc.perform(post("/api/v1/knowledge/search").with(httpBasic(user, password))
                        .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + query + "\",\"topK\":" + topK + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Cookie csrfCookie(String user, String password) throws Exception {
        Cookie cookie = mvc.perform(get("/api/v1/me/csrf").with(httpBasic(user, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String json(String title, String content) {
        return "{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}";
    }

    @TestConfiguration
    static class EmbeddingsForTests {
        @Bean
        EmbeddingClient fakeEmbeddings() {
            return new EmbeddingClient() {
                @Override public String model() { return "fake-test-768"; }
                @Override public List<double[]> embed(List<String> texts) {
                    return texts.stream().map(text -> {
                        if (text.contains("FALHA-EMBEDDING")) throw new EmbeddingUnavailableException();
                        String lower = text.toLowerCase(Locale.ROOT);
                        double[] vector = new double[768];
                        vector[0] = lower.contains("pagamento") ? 1 : 0;
                        vector[1] = lower.contains("reserva") ? 1 : 0;
                        vector[2] = 0.01;
                        return vector;
                    }).toList();
                }
            };
        }
    }
}
