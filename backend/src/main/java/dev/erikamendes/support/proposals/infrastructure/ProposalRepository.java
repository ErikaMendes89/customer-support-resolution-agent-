package dev.erikamendes.support.proposals.infrastructure;

import dev.erikamendes.support.proposals.domain.ResolutionProposal;
import dev.erikamendes.support.proposals.domain.ResolutionProposal.Source;
import java.time.OffsetDateTime;
import java.util.List;
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
        return jdbc.query("SELECT id, case_id, status, answer, generation_model, embedding_model, created_by, created_at FROM resolution_proposals WHERE case_id = ? AND organization_id = ? ORDER BY created_at DESC, id DESC LIMIT 50",
                (rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new ResolutionProposal(id, rs.getObject("case_id", UUID.class), rs.getString("status"),
                            rs.getString("answer"), rs.getString("generation_model"), rs.getString("embedding_model"),
                            rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class), sources(id, org));
                }, caseId, org);
    }

    private List<Source> sources(UUID id, UUID org) {
        return jdbc.query("SELECT source_key, document_id, document_title, chunk_ordinal, content_snapshot, similarity FROM proposal_sources WHERE proposal_id = ? AND organization_id = ? ORDER BY source_key",
                (rs, row) -> new Source(rs.getString("source_key"), rs.getObject("document_id", UUID.class),
                        rs.getString("document_title"), rs.getInt("chunk_ordinal"), rs.getString("content_snapshot"),
                        rs.getDouble("similarity")), id, org);
    }

    public boolean sourcesPresent(UUID org, List<Source> sources) {
        for (Source source : sources) {
            Boolean present = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM document_chunks WHERE organization_id = ? AND document_id = ? AND ordinal = ? AND content = ?)",
                    Boolean.class, org, source.documentId(), source.chunkOrdinal(), source.content());
            if (!Boolean.TRUE.equals(present)) return false;
        }
        return true;
    }
}
