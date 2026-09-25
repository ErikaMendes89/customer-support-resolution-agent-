package dev.erikamendes.support.proposals.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.erikamendes.support.cases.application.CaseNotFoundException;
import dev.erikamendes.support.cases.domain.CaseStatus;
import dev.erikamendes.support.cases.infrastructure.CaseRepository;
import dev.erikamendes.support.knowledge.application.EmbeddingClient;
import dev.erikamendes.support.knowledge.application.KnowledgeService;
import dev.erikamendes.support.proposals.domain.ResolutionProposal;
import dev.erikamendes.support.proposals.domain.ResolutionProposal.Source;
import dev.erikamendes.support.proposals.infrastructure.ProposalRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProposalService {
    private static final String FALLBACK = "Evidências insuficientes para propor uma resposta. Consulte a base de conhecimento e revise o caso.";
    private static final Pattern CITATION = Pattern.compile("\\[S[0-9]+\\]");
    private final CaseRepository cases;
    private final KnowledgeService knowledge;
    private final EmbeddingClient embeddings;
    private final GenerationClient generation;
    private final ProposalRepository proposals;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final double minimumSimilarity;

    public ProposalService(CaseRepository cases, KnowledgeService knowledge, EmbeddingClient embeddings,
                           GenerationClient generation, ProposalRepository proposals, ObjectMapper mapper, TransactionTemplate transactions,
                           @Value("${app.proposals.minimum-similarity:0.55}") double minimumSimilarity) {
        this.cases = cases; this.knowledge = knowledge; this.embeddings = embeddings;
        this.generation = generation; this.proposals = proposals; this.mapper = mapper; this.transactions = transactions;
        if (minimumSimilarity < -1 || minimumSimilarity > 1 || !Double.isFinite(minimumSimilarity))
            throw new IllegalArgumentException("Invalid minimum similarity");
        this.minimumSimilarity = minimumSimilarity;
    }

    public ResolutionProposal generate(UUID caseId, UUID org, String actor, String requestKey) {
        if (requestKey != null && (requestKey.isBlank() || requestKey.length() > 80
                || !requestKey.matches("[A-Za-z0-9_-]+"))) throw new InvalidIdempotencyKeyException();
        var supportCase = cases.find(caseId, org).orElseThrow(CaseNotFoundException::new);
        if (requestKey != null) {
            var completed = proposals.completedRequest(caseId, org, requestKey);
            if (completed.isPresent()) return proposals.find(completed.get(), caseId, org).orElseThrow();
        }
        if (terminal(supportCase.status())) throw new ProposalConflictException();
        if (requestKey != null && !transactions.execute(status -> proposals.reserveRequest(caseId, org, requestKey))) {
            var completed = proposals.completedRequest(caseId, org, requestKey);
            if (completed.isPresent()) return proposals.find(completed.get(), caseId, org).orElseThrow();
            throw new ProposalConflictException();
        }
        try {
            String query = (supportCase.title() + " " + supportCase.description());
            if (query.length() > 500) query = query.substring(0, 500);
            var hits = knowledge.search(org, query, 5).stream()
                    .filter(hit -> Double.isFinite(hit.similarity()) && hit.similarity() >= minimumSimilarity).toList();
            List<Source> candidates = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                var hit = hits.get(i);
                candidates.add(new Source("S" + (i + 1), hit.documentId(), hit.documentTitle(),
                        hit.chunkOrdinal(), hit.content(), hit.similarity()));
            }
            String response = candidates.isEmpty() ? null : generation.generate(supportCase.title(), supportCase.description(), candidates);
            return transactions.execute(status -> {
                ResolutionProposal proposal = persist(caseId, org, actor, candidates, response);
                if (requestKey != null) proposals.completeRequest(caseId, org, requestKey, proposal.id());
                return proposal;
            });
        } catch (RuntimeException error) {
            if (requestKey != null) transactions.execute(status -> {
                proposals.releaseRequest(caseId, org, requestKey); return null;
            });
            throw error;
        }
    }

    public ResolutionProposal persist(UUID caseId, UUID org, String actor, List<Source> candidates, String response) {
        var current = cases.findForUpdate(caseId, org).orElseThrow(CaseNotFoundException::new);
        if (terminal(current.status()) || !proposals.sourcesPresent(org, candidates)) throw new ProposalConflictException();
        String answer = parseAnswer(response);
        Set<String> cited = new HashSet<>();
        if (answer != null) {
            Matcher matcher = CITATION.matcher(answer);
            while (matcher.find()) cited.add(matcher.group().substring(1, matcher.group().length() - 1));
        }
        Set<String> allowed = new HashSet<>();
        for (Source source : candidates) allowed.add(source.key());
        boolean valid = answer != null && !answer.isBlank() && !cited.isEmpty() && allowed.containsAll(cited)
                && answer.length() <= 3000 && !answer.contains("```")
                && !answer.replaceAll("\\[S[0-9]+\\]", "").matches("(?s).*\\[[^\\]]*S[0-9]+[^\\]]*\\].*");
        List<Source> used = valid ? candidates.stream().filter(source -> cited.contains(source.key())).toList() : List.of();
        UUID id = UUID.randomUUID();
        proposals.insert(id, caseId, org, valid ? "READY_FOR_REVIEW" : "INSUFFICIENT_EVIDENCE",
                valid ? answer : FALLBACK, generation.model(), embeddings.model(), actor, used);
        return proposals.find(id, caseId, org).orElseThrow();
    }

    private String parseAnswer(String response) {
        if (response == null || response.length() > 10000) return null;
        try {
            JsonNode node = mapper.readTree(response);
            if (!node.isObject() || !node.path("answer").isTextual()) return null;
            return node.path("answer").asText().trim();
        } catch (Exception ignored) { return null; }
    }

    private boolean terminal(CaseStatus status) { return status == CaseStatus.RESOLVED || status == CaseStatus.CLOSED; }

    public List<ResolutionProposal> list(UUID caseId, UUID org) {
        cases.find(caseId, org).orElseThrow(CaseNotFoundException::new);
        return proposals.list(caseId, org);
    }

    public ResolutionProposal get(UUID caseId, UUID proposalId, UUID org) {
        cases.find(caseId, org).orElseThrow(CaseNotFoundException::new);
        return proposals.find(proposalId, caseId, org).orElseThrow(CaseNotFoundException::new);
    }
}
