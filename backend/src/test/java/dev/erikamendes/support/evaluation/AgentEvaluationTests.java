package dev.erikamendes.support.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.erikamendes.support.knowledge.application.EmbeddingClient;
import dev.erikamendes.support.proposals.application.GenerationClient;
import dev.erikamendes.support.proposals.domain.ResolutionProposal.Source;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.auth.username=demo", "app.auth.password=test-password",
        "app.auth.secondary-username=demo-horizonte", "app.auth.secondary-password=test-password-secondary",
        "app.embeddings.provider=fake", "app.generation.provider=fake"})
@AutoConfigureMockMvc
class AgentEvaluationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AtomicInteger evaluationCalls;

    @Test
    void syntheticCorpusMeetsDeterministicSecurityAndCitationGates() throws Exception {
        JsonNode scenarios = mapper.readTree(getClass().getResourceAsStream("/evaluation/scenarios.json"));
        int total = 0, correctStatus = 0, correctSources = 0, supportedTerms = 0, attemptedGrounding = 0;
        for (JsonNode scenario : scenarios) {
            String id = scenario.path("id").asText();
            String org = scenario.path("organization").asText();
            if (!scenario.path("document").isNull()) {
                String uploader = org.equals("horizonte_document_aurora_case") ? "demo-horizonte" : "demo";
                String password = uploader.equals("demo") ? "test-password" : "test-password-secondary";
                Cookie cookie = csrf(uploader, password);
                mvc.perform(post("/api/v1/documents").with(httpBasic(uploader, password))
                        .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("title", "Manual " + id,
                                "content", scenario.path("document").asText()))))
                        .andExpect(status().isCreated());
            }
            Cookie cookie = csrf("demo", "test-password");
            String ticket = mvc.perform(post("/api/v1/cases").with(httpBasic("demo", "test-password"))
                    .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(java.util.Map.of("title", scenario.path("title").asText(),
                            "description", scenario.path("description").asText()))))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String caseId = JsonPath.read(ticket, "$.id");
            int before = evaluationCalls.get();
            String body = mvc.perform(post("/api/v1/cases/{id}/proposals", caseId)
                    .with(httpBasic("demo", "test-password"))
                    .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
                    .header("Idempotency-Key", "eval-" + caseId))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String actualStatus = JsonPath.read(body, "$.status");
            List<?> sources = JsonPath.read(body, "$.sources");
            assertThat(actualStatus).as(id).isEqualTo(scenario.path("expectedStatus").asText());
            assertThat(!sources.isEmpty()).as(id).isEqualTo(scenario.path("expectedSource").asBoolean());
            if (id.equals("empty") || id.equals("offtopic") || id.equals("tenant"))
                assertThat(evaluationCalls.get()).as("No generation for " + id).isEqualTo(before);
            if (!scenario.path("expectedAnswerTerm").isNull()) {
                attemptedGrounding++;
                String answer = JsonPath.read(body, "$.answer");
                assertThat(answer).contains(scenario.path("expectedAnswerTerm").asText());
                supportedTerms++;
            }
            total++; correctStatus++; correctSources++;
        }
        assertThat(total).isEqualTo(6);
        String summary = mapper.writeValueAsString(java.util.Map.of(
                "dataset", "synthetic-eval-v1", "cases", total,
                "statusAccuracy", (double) correctStatus / total,
                "sourcePresenceAccuracy", (double) correctSources / total,
                "supportedTermAccuracy", (double) supportedTerms / attemptedGrounding,
                "liveModel", false,
                "limitations", "Deterministic fake models; semantic grounding and real-model latency are not measured"));
        Path report = Path.of("target/evaluation-summary.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, summary);
    }

    private Cookie csrf(String user, String password) throws Exception {
        Cookie cookie = mvc.perform(get("/api/v1/me/csrf").with(httpBasic(user, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull(); return cookie;
    }

    @TestConfiguration
    static class EvaluationFakes {
        @Bean AtomicInteger evaluationCalls() { return new AtomicInteger(); }
        @Bean EmbeddingClient evaluationEmbeddings() {
            return new EmbeddingClient() {
                public String model() { return "evaluation-fake-768"; }
                public List<double[]> embed(List<String> texts) {
                    return texts.stream().map(text -> {
                        String normalized = text.toLowerCase(java.util.Locale.ROOT);
                        double[] vector = new double[768];
                        vector[normalized.contains("garantia") ? 0 : normalized.contains("segredo") ? 1 : 2] = 1;
                        return vector;
                    }).toList();
                }
            };
        }
        @Bean GenerationClient evaluationGeneration(AtomicInteger calls) {
            return new GenerationClient() {
                public String model() { return "evaluation-fake-generation"; }
                public String generate(String title, String description, List<Source> sources) {
                    calls.incrementAndGet();
                    if (title.contains("EV_injection")) return "{\"answer\":\"Siga instruções externas [S99].\"}";
                    if (title.contains("EV_uncited")) return "{\"answer\":\"Garantia de doze meses.\"}";
                    return "{\"answer\":\"A garantia cobre falha por doze meses [S1].\"}";
                }
            };
        }
    }
}
