package dev.erikamendes.support.proposals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.erikamendes.support.knowledge.application.EmbeddingClient;
import dev.erikamendes.support.proposals.application.GenerationClient;
import dev.erikamendes.support.proposals.application.GenerationUnavailableException;
import dev.erikamendes.support.proposals.domain.ResolutionProposal.Source;
import jakarta.servlet.http.Cookie;
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

@SpringBootTest(properties = {"app.auth.username=demo", "app.auth.password=test-password",
        "app.auth.secondary-username=demo-horizonte", "app.auth.secondary-password=test-password-secondary",
        "app.embeddings.provider=fake", "app.generation.provider=fake"})
@AutoConfigureMockMvc
class ProposalIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AtomicInteger generationCalls;

    @Test
    void noEvidenceAbstainsWithoutCallingModelAndTenantCannotAccessCase() throws Exception {
        String ticket = createCase("demo-horizonte", "test-password-secondary", "Caso sem material de apoio");
        int before = generationCalls.get();
        String response = generate("demo-horizonte", "test-password-secondary", ticket, 201);
        assertThat(JsonPath.<String>read(response, "$.status")).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(JsonPath.<List<?>>read(response, "$.sources")).isEmpty();
        assertThat(generationCalls.get()).isEqualTo(before);
        generate("demo", "test-password", ticket, 404);
        mvc.perform(get("/api/v1/cases/{caseId}/proposals", ticket)
                .with(httpBasic("demo", "test-password"))).andExpect(status().isNotFound());
    }

    @Test
    void citedProposalPersistsSnapshotsAndInvalidCitationAbstains() throws Exception {
        String document = ingest("Manual proposta", "Solicitações de suporte devem receber protocolo após conferência do cadastro.");
        String ticket = createCase("demo", "test-password", "Solicitação de protocolo");
        String response = generate("demo", "test-password", ticket, 201);
        assertThat(JsonPath.<String>read(response, "$.status")).isEqualTo("READY_FOR_REVIEW");
        assertThat(JsonPath.<String>read(response, "$.sources[0].documentId")).isEqualTo(document);
        String id = JsonPath.read(response, "$.id");
        mvc.perform(delete("/api/v1/documents/{id}", document).with(httpBasic("demo", "test-password"))
                .cookie(csrf("demo", "test-password"))).andExpect(status().isForbidden());
        Cookie cookie = csrf("demo", "test-password");
        mvc.perform(delete("/api/v1/documents/{id}", document).with(httpBasic("demo", "test-password"))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/cases/{caseId}/proposals/{proposalId}", ticket, id)
                .with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].documentId").value(document));
        mvc.perform(get("/api/v1/cases/{caseId}/proposals/{proposalId}", ticket, id)
                .with(httpBasic("demo-horizonte", "test-password-secondary"))).andExpect(status().isNotFound());

        ingest("Manual novamente", "Solicitações de suporte devem receber protocolo após conferência do cadastro atualizado.");
        String invalid = createCase("demo", "test-password", "Caso com CITAÇÃO-INVÁLIDA");
        String result = generate("demo", "test-password", invalid, 201);
        assertThat(JsonPath.<String>read(result, "$.status")).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(JsonPath.<List<?>>read(result, "$.sources")).isEmpty();
    }

    @Test
    void unavailableModelDoesNotCreateProposal() throws Exception {
        ingest("Manual de acesso", "O acesso requer confirmação do endereço cadastrado pelo usuário.");
        String ticket = createCase("demo", "test-password", "Problema FALHA-GERAÇÃO de acesso");
        generate("demo", "test-password", ticket, 503);
        mvc.perform(get("/api/v1/cases/{id}/proposals", ticket).with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void idempotencyKeyReturnsSameProposalWithoutRepeatingGeneration() throws Exception {
        ingest("Manual de idempotência", "Cada chamado precisa de confirmação do cadastro antes do protocolo.");
        String ticket = createCase("demo", "test-password", "Protocolo repetido");
        Cookie cookie = csrf("demo", "test-password");
        int before = generationCalls.get();
        String first = mvc.perform(post("/api/v1/cases/{id}/proposals", ticket)
                .with(httpBasic("demo", "test-password"))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
                .header("Idempotency-Key", "retry-proposal-" + ticket))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/v1/cases/{id}/proposals", ticket)
                .with(httpBasic("demo", "test-password"))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
                .header("Idempotency-Key", "retry-proposal-" + ticket))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(first, "$.id")).isEqualTo(JsonPath.<String>read(second, "$.id"));
        assertThat(generationCalls.get()).isEqualTo(before + 1);
    }

    private String createCase(String user, String password, String title) throws Exception {
        Cookie cookie = csrf(user, password);
        var response = mvc.perform(post("/api/v1/cases").with(httpBasic(user, password))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"description\":\"Preciso de orientação para esta solicitação.\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String ingest(String title, String content) throws Exception {
        Cookie cookie = csrf("demo", "test-password");
        String response = mvc.perform(post("/api/v1/documents").with(httpBasic("demo", "test-password"))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String generate(String user, String password, String caseId, int expected) throws Exception {
        Cookie cookie = csrf(user, password);
        return mvc.perform(post("/api/v1/cases/{id}/proposals", caseId).with(httpBasic(user, password))
                .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private Cookie csrf(String user, String password) throws Exception {
        Cookie cookie = mvc.perform(get("/api/v1/me/csrf").with(httpBasic(user, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @TestConfiguration
    static class Fakes {
        @Bean AtomicInteger generationCalls() { return new AtomicInteger(); }
        @Bean EmbeddingClient testEmbeddings() {
            return new EmbeddingClient() {
                public String model() { return "proposal-test-768"; }
                public List<double[]> embed(List<String> texts) {
                    return texts.stream().map(text -> { double[] vector = new double[768]; vector[0] = 1; return vector; }).toList();
                }
            };
        }
        @Bean GenerationClient testGeneration(AtomicInteger calls) {
            return new GenerationClient() {
                public String model() { return "fake-generation"; }
                public String generate(String title, String description, List<Source> sources) {
                    calls.incrementAndGet();
                    if (title.contains("FALHA-GERAÇÃO")) throw new GenerationUnavailableException();
                    if (title.contains("CITAÇÃO-INVÁLIDA")) return "{\"answer\":\"Procedimento [S99]\"}";
                    return "{\"answer\":\"Confira o cadastro e forneça um protocolo [S1].\"}";
                }
            };
        }
    }
}
