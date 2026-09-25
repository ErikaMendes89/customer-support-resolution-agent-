package dev.erikamendes.support.proposals.infrastructure;

import dev.erikamendes.support.proposals.domain.ResolutionProposal;
import dev.erikamendes.support.proposals.domain.ResolutionProposal.Source;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProposalRepository {
    private final JdbcTemplate jdbc;
    public ProposalRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID id, UUID caseId, UUID org, String status, String answer,
                       String generationModel, String embeddingModel, String actor, List<Source> sources) {
        jdbc.update("INSERT INTO resolution_proposals (id, case_id, organization_id, status, answer, generation_model, embedding_model, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, caseId, org, status, answer, generationModel, embeddingModel, actor);
        for (Source source : sources) jdbc.update("INSERT INTO proposal_sources (proposal_id, organization_id, source_key, document_id, document_title, chunk_ordinal, content_snapshot, similarity) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, org, source.key(), source.documentId(), source.documentTitle(), source.chunkOrdinal(), source.content(), source.similarity());
    }

    public Optional<ResolutionProposal> find(UUID id, UUID caseId, UUID org) {
        return jdbc.query("SELECT id, case_id, status, answer, generation_model, embedding_model, created_by, created_at FROM resolution_proposals WHERE id = ? AND case_id = ? AND organization_id = ?",
                (rs, row) -> new ResolutionProposal(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("status"), rs.getString("answer"), rs.getString("generation_model"),
                        rs.getString("embedding_model"), rs.getString("created_by"),
                        rs.getObject("created_at", OffsetDateTime.class), sources(id, org)), id, caseId, org).stream().findFirst();
    }

    public List<ResolutionProposal> list(UUID caseId, UUID org) {
        List<ResolutionProposal> rows = jdbc.query("SELECT id, case_id, status, answer, generation_model, embedding_model, created_by, created_at FROM resolution_proposals WHERE case_id = ? AND organization_id = ? ORDER BY created_at DESC, id DESC LIMIT 50",
                (rs, row) -> new ResolutionProposal(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("status"), rs.getString("answer"), rs.getString("generation_model"),
                        rs.getString("embedding_model"), rs.getString("created_by"),
                        rs.getObject("created_at", OffsetDateTime.class), List.of()), caseId, org);
        if (rows.isEmpty()) return rows;
        Map<UUID, List<Source>> byProposal = new HashMap<>();
        jdbc.query("SELECT s.proposal_id, s.source_key, s.document_id, s.document_title, s.chunk_ordinal, s.content_snapshot, s.similarity "
                        + "FROM proposal_sources s JOIN resolution_proposals p ON p.id = s.proposal_id "
                        + "AND p.organization_id = s.organization_id WHERE p.case_id = ? AND p.organization_id = ? "
                        + "AND s.proposal_id IN (SELECT id FROM resolution_proposals WHERE case_id = ? "
                        + "AND organization_id = ? ORDER BY created_at DESC, id DESC LIMIT 50) ORDER BY s.proposal_id, s.source_key",
                rs -> {
                    UUID id = rs.getObject("proposal_id", UUID.class);
                    byProposal.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new Source(
                            rs.getString("source_key"), rs.getObject("document_id", UUID.class),
                            rs.getString("document_title"), rs.getInt("chunk_ordinal"),
                            rs.getString("content_snapshot"), rs.getDouble("similarity")));
                }, caseId, org, caseId, org);
        return rows.stream().map(row -> new ResolutionProposal(row.id(), row.caseId(), row.status(),
                row.answer(), row.generationModel(), row.embeddingModel(), row.createdBy(),
                row.createdAt(), byProposal.getOrDefault(row.id(), List.of()))).toList();
    }

    private List<Source> sources(UUID id, UUID org) {
        return jdbc.query("SELECT source_key, document_id, document_title, chunk_ordinal, content_snapshot, similarity FROM proposal_sources WHERE proposal_id = ? AND organization_id = ? ORDER BY source_key",
                (rs, row) -> new Source(rs.getString("source_key"), rs.getObject("document_id", UUID.class),
                        rs.getString("document_title"), rs.getInt("chunk_ordinal"), rs.getString("content_snapshot"),
                        rs.getDouble("similarity")), id, org);
    }

    public Optional<UUID> completedRequest(UUID caseId, UUID org, String key) {
        return jdbc.query("SELECT proposal_id FROM proposal_generation_requests WHERE case_id = ? AND organization_id = ? AND request_key = ? AND state = 'COMPLETED'",
                (rs, row) -> rs.getObject("proposal_id", UUID.class), caseId, org, key).stream().findFirst();
    }

    public boolean reserveRequest(UUID caseId, UUID org, String key) {
        // An abandoned reservation may be reclaimed after the provider timeouts plus a safety margin.
        jdbc.update("DELETE FROM proposal_generation_requests WHERE case_id = ? AND organization_id = ? AND request_key = ? AND state = 'PROCESSING' AND created_at < now() - interval '10 minutes'",
                caseId, org, key);
        return jdbc.update("INSERT INTO proposal_generation_requests (organization_id, case_id, request_key, state) VALUES (?, ?, ?, 'PROCESSING') ON CONFLICT DO NOTHING",
                org, caseId, key) == 1;
    }

    public void completeRequest(UUID caseId, UUID org, String key, UUID proposalId) {
        int updated = jdbc.update("UPDATE proposal_generation_requests SET state = 'COMPLETED', proposal_id = ? WHERE case_id = ? AND organization_id = ? AND request_key = ? AND state = 'PROCESSING'",
                proposalId, caseId, org, key);
        if (updated != 1) throw new IllegalStateException("Generation reservation was lost");
    }

    public void releaseRequest(UUID caseId, UUID org, String key) {
        jdbc.update("DELETE FROM proposal_generation_requests WHERE case_id = ? AND organization_id = ? AND request_key = ? AND state = 'PROCESSING'",
                caseId, org, key);
    }

    public boolean sourcesPresent(UUID org, List<Source> sources) {
        for (Source source : sources) {
            // Hold the chunk until the proposal and its snapshots commit. Deletion then waits for us.
            boolean present = !jdbc.query("SELECT id FROM document_chunks WHERE organization_id = ? AND document_id = ? AND ordinal = ? AND content = ? FOR SHARE",
                    (rs, row) -> rs.getLong("id"), org, source.documentId(), source.chunkOrdinal(), source.content()).isEmpty();
            if (!present) return false;
        }
        return true;
    }
}
