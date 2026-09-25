package dev.erikamendes.support.review.infrastructure;

import dev.erikamendes.support.review.domain.ProposalReview;
import dev.erikamendes.support.review.domain.ProposalReview.Decision;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRepository {
    private final JdbcTemplate jdbc;
    public ReviewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<String> lockProposal(UUID id, UUID caseId, UUID org) {
        return jdbc.query("SELECT status FROM resolution_proposals WHERE id = ? AND case_id = ? AND organization_id = ? FOR UPDATE",
                (rs, row) -> rs.getString("status"), id, caseId, org).stream().findFirst();
    }

    public ProposalReview insert(UUID proposalId, UUID caseId, UUID org, Decision decision,
                                 String answer, String note, String actor) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO proposal_reviews (id, proposal_id, case_id, organization_id, decision, final_answer, note, reviewed_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, proposalId, caseId, org, decision.name(), answer, note, actor);
        return find(proposalId, caseId, org).orElseThrow();
    }

    public Optional<ProposalReview> find(UUID proposalId, UUID caseId, UUID org) {
        return jdbc.query("SELECT id, proposal_id, case_id, decision, final_answer, note, reviewed_by, reviewed_at FROM proposal_reviews WHERE proposal_id = ? AND case_id = ? AND organization_id = ?",
                (rs, row) -> map(rs), proposalId, caseId, org).stream().findFirst();
    }

    public List<ProposalReview> list(UUID caseId, UUID org) {
        return jdbc.query("SELECT id, proposal_id, case_id, decision, final_answer, note, reviewed_by, reviewed_at FROM proposal_reviews WHERE case_id = ? AND organization_id = ? ORDER BY reviewed_at DESC, id DESC LIMIT 50",
                (rs, row) -> map(rs), caseId, org);
    }

    private ProposalReview map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProposalReview(rs.getObject("id", UUID.class), rs.getObject("proposal_id", UUID.class),
                rs.getObject("case_id", UUID.class), Decision.valueOf(rs.getString("decision")),
                rs.getString("final_answer"), rs.getString("note"), rs.getString("reviewed_by"),
                rs.getObject("reviewed_at", OffsetDateTime.class));
    }
}
