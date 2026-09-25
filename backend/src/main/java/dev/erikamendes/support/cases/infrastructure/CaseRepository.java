package dev.erikamendes.support.cases.infrastructure;

import dev.erikamendes.support.cases.domain.CaseEvent;
import dev.erikamendes.support.cases.domain.CaseStatus;
import dev.erikamendes.support.cases.domain.SupportCase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CaseRepository {
    private static final String CASE_COLUMNS = "id, organization_id, title, description, status, created_by, created_at, updated_at";
    private final JdbcTemplate jdbc;

    public CaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID organizationId, String title, String description, String actor) {
        jdbc.update("INSERT INTO support_cases (id, organization_id, title, description, status, created_by) "
                        + "VALUES (?, ?, ?, ?, 'OPEN', ?)", id, organizationId, title, description, actor);
    }

    public Optional<SupportCase> find(UUID id, UUID organizationId) {
        return jdbc.query("SELECT " + CASE_COLUMNS + " FROM support_cases WHERE id = ? AND organization_id = ?",
                this::mapCase, id, organizationId).stream().findFirst();
    }

    public Optional<SupportCase> findForUpdate(UUID id, UUID organizationId) {
        return jdbc.query("SELECT " + CASE_COLUMNS
                        + " FROM support_cases WHERE id = ? AND organization_id = ? FOR UPDATE",
                this::mapCase, id, organizationId).stream().findFirst();
    }

    public List<SupportCase> list(UUID organizationId, int page, int size) {
        return jdbc.query("SELECT " + CASE_COLUMNS
                        + " FROM support_cases WHERE organization_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                this::mapCase, organizationId, size, page * size);
    }

    public long count(UUID organizationId) {
        Long result = jdbc.queryForObject(
                "SELECT count(*) FROM support_cases WHERE organization_id = ?", Long.class, organizationId);
        return result == null ? 0 : result;
    }

    public void updateStatus(UUID id, UUID organizationId, CaseStatus status) {
        jdbc.update("UPDATE support_cases SET status = ?, updated_at = now() WHERE id = ? AND organization_id = ?",
                status.name(), id, organizationId);
    }

    public void appendEvent(UUID id, UUID organizationId, String type, CaseStatus from, CaseStatus to,
                            String note, String actor) {
        jdbc.update("INSERT INTO case_events (case_id, organization_id, event_type, from_status, to_status, note, actor) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, organizationId, type, from == null ? null : from.name(), to.name(), note, actor);
    }

    public List<CaseEvent> events(UUID id, UUID organizationId) {
        return jdbc.query("SELECT id, event_type, from_status, to_status, note, actor, occurred_at "
                        + "FROM case_events WHERE case_id = ? AND organization_id = ? ORDER BY id ASC",
                (rs, row) -> new CaseEvent(rs.getLong("id"), rs.getString("event_type"),
                        rs.getString("from_status") == null ? null : CaseStatus.valueOf(rs.getString("from_status")),
                        CaseStatus.valueOf(rs.getString("to_status")), rs.getString("note"),
                        rs.getString("actor"), rs.getObject("occurred_at", OffsetDateTime.class)),
                id, organizationId);
    }

    private SupportCase mapCase(ResultSet rs, int row) throws SQLException {
        return new SupportCase(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getString("title"), rs.getString("description"), CaseStatus.valueOf(rs.getString("status")),
                rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
