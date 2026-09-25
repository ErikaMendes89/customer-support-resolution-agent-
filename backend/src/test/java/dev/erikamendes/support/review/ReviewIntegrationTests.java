package dev.erikamendes.support.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.erikamendes.support.knowledge.application.EmbeddingClient;
import dev.erikamendes.support.proposals.application.GenerationClient;
import dev.erikamendes.support.proposals.domain.ResolutionProposal.Source;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.auth.username=demo", "app.auth.password=test-password",
        "app.auth.secondary-username=demo-horizonte", "app.auth.secondary-password=test-password-secondary",
        "app.embeddings.provider=fake", "app.generation.provider=fake"})
@AutoConfigureMockMvc
class ReviewIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void approvesOnceAndPreservesAuditableDecision() throws Exception {
        ingest();
        String caseId = createCase("demo", "test-password");
        String proposalId = generate(caseId, "demo", "test-password");
        mvc.perform(post("/api/v1/cases/{id}/proposals/{proposalId}/review", caseId, proposalId)
                .with(httpBasic("demo", "test-password")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        String answer = review(caseId, proposalId, "demo", "test-password",
                "{\"decision\":\"APPROVED\"}", 201);
        assertThat(JsonPath.<String>read(answer, "$.finalAnswer")).contains("[S1]");
        assertThat(JsonPath.<String>read(answer, "$.reviewedBy")).isEqualTo("demo");
        review(caseId, proposalId, "demo", "test-password", "{\"decision\":\"REJECTED\",\"note\":\"não\"}", 409);
        mvc.perform(get("/api/v1/cases/{id}/reviews", caseId).with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].proposalId").value(proposalId));
        mvc.perform(get("/api/v1/cases/{id}/proposals/{proposalId}/review", caseId, proposalId)
                .with(httpBasic("demo-horizonte", "test-password-secondary")))
                .andExpect(status().isNotFound());
    }

    @Test
    void editingRequiresKnownCitationAndReasonAndRejectingNeedsReason() throws Exception {
        ingest();
        String caseId = createCase("demo", "test-password");
        String proposalId = generate(caseId, "demo", "test-password");
        review(caseId, proposalId, "demo-horizonte", "test-password-secondary",
                "{\"decision\":\"APPROVED\"}", 404);
        review(caseId, proposalId, "demo", "test-password",
                "{\"decision\":\"EDITED\",\"editedAnswer\":\"Texto falso [S99]\",\"note\":\"correção\"}", 400);
        review(caseId, proposalId, "demo", "test-password",
                "{\"decision\":\"EDITED\",\"editedAnswer\":\"Texto sem citação\",\"note\":\"correção\"}", 400);
        review(caseId, proposalId, "demo", "test-password",
                "{\"decision\":\"EDITED\",\"editedAnswer\":\"Texto revisado [S1].\"}", 400);
        String result = review(caseId, proposalId, "demo", "test-password",
                "{\"decision\":\"EDITED\",\"editedAnswer\":\"Texto revisado [S1].\",\"note\":\"Detalhei a orientação\"}", 201);
        assertThat(JsonPath.<String>read(result, "$.finalAnswer")).isEqualTo("Texto revisado [S1].");

        String rejected = generate(caseId, "demo", "test-password");
        review(caseId, rejected, "demo", "test-password", "{\"decision\":\"REJECTED\"}", 400);
        review(caseId, rejected, "demo", "test-password", "{\"decision\":\"REJECTED\",\"note\":\"Não procede\"}", 201);
        mvc.perform(get("/api/v1/cases/{id}/proposals/{proposalId}/review", caseId, rejected)
                .with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalAnswer").isEmpty());
    }

    @Test
    void insufficientEvidenceCanOnlyBeRejectedAndClosedCaseCannotBeReviewed() throws Exception {
        String otherCase = createCase("demo-horizonte", "test-password-secondary");
        String insufficient = generate(otherCase, "demo-horizonte", "test-password-secondary");
        review(otherCase, insufficient, "demo-horizonte", "test-password-secondary",
                "{\"decision\":\"APPROVED\"}", 409);
        review(otherCase, insufficient, "demo-horizonte", "test-password-secondary",
                "{\"decision\":\"REJECTED\",\"note\":\"Sem documentos\"}", 201);

        ingest();
        String caseId = createCase("demo", "test-password");
        String proposalId = generate(caseId, "demo", "test-password");
        Cookie cookie = csrf("demo", "test-password");
        mvc.perform(patch("/api/v1/cases/{id}/status", caseId).with(httpBasic("demo", "test-password"))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
        review(caseId, proposalId, "demo", "test-password", "{\"decision\":\"APPROVED\"}", 409);
    }

    private String createCase(String user, String password) throws Exception {
        Cookie cookie = csrf(user, password);
        String response = mvc.perform(post("/api/v1/cases").with(httpBasic(user, password))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Solicitação de suporte\",\"description\":\"Preciso de ajuda para obter meu protocolo de atendimento.\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void ingest() throws Exception {
        Cookie cookie = csrf("demo", "test-password");
        mvc.perform(post("/api/v1/documents").with(httpBasic("demo", "test-password"))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Manual de protocolo\",\"content\":\"O protocolo é entregue após a confirmação dos dados cadastrais do solicitante.\"}"))
                .andExpect(status().isCreated());
    }

    private String generate(String id, String user, String password) throws Exception {
        Cookie cookie = csrf(user, password);
        String response = mvc.perform(post("/api/v1/cases/{id}/proposals", id).with(httpBasic(user, password))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String review(String caseId, String proposalId, String user, String password, String payload, int expected) throws Exception {
        Cookie cookie = csrf(user, password);
        return mvc.perform(post("/api/v1/cases/{id}/proposals/{proposalId}/review", caseId, proposalId)
                .with(httpBasic(user, password)).cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private Cookie csrf(String user, String password) throws Exception {
        Cookie cookie = mvc.perform(get("/api/v1/me/csrf").with(httpBasic(user, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull(); return cookie;
    }

    @TestConfiguration
    static class Fakes {
        @Bean EmbeddingClient embeddingsForReview() {
            return new EmbeddingClient() {
                public String model() { return "review-test-768"; }
                public List<double[]> embed(List<String> texts) {
                    return texts.stream().map(text -> { double[] vector = new double[768]; vector[0] = 1; return vector; }).toList();
                }
            };
        }
        @Bean GenerationClient generationForReview() {
            return new GenerationClient() {
                public String model() { return "review-fake-generation"; }
                public String generate(String title, String description, List<Source> sources) {
                    return "{\"answer\":\"Confira os dados cadastrais antes de fornecer o protocolo [S1].\"}";
                }
            };
        }
    }
}
