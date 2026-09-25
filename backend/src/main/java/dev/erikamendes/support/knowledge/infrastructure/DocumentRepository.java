package dev.erikamendes.support.knowledge.infrastructure;

import dev.erikamendes.support.knowledge.application.KnowledgeService.IngestResult;
import dev.erikamendes.support.knowledge.domain.DocumentChunk;
import dev.erikamendes.support.knowledge.domain.KnowledgeDocument;
import dev.erikamendes.support.knowledge.domain.SearchHit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DocumentRepository {
    private static final String DOCUMENT_COLUMNS = "id, title, embedding_model, chunk_count, created_by, created_at";
    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<KnowledgeDocument> find(UUID id, UUID organizationId) {
        return jdbc.query("SELECT " + DOCUMENT_COLUMNS
                        + " FROM knowledge_documents WHERE id = ? AND organization_id = ?",
                this::mapDocument, id, organizationId).stream().findFirst();
    }

    public Optional<KnowledgeDocument> findByHash(UUID organizationId, String hash, String model) {
        return jdbc.query("SELECT " + DOCUMENT_COLUMNS
                        + " FROM knowledge_documents WHERE organization_id = ? AND content_sha256 = ? AND embedding_model = ?",
                this::mapDocument, organizationId, hash, model).stream().findFirst();
    }

    @Transactional
    public IngestResult insert(UUID organizationId, String actor, String title, String hash,
                               String model, List<String> chunks, List<double[]> vectors) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("INSERT INTO knowledge_documents "
                        + "(id, organization_id, title, content_sha256, embedding_model, chunk_count, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT "
                        + "(organization_id, content_sha256, embedding_model) DO NOTHING",
                id, organizationId, title, hash, model, chunks.size(), actor);
        if (inserted == 0) {
            return new IngestResult(findByHash(organizationId, hash, model).orElseThrow(), false);
        }
        for (int ordinal = 0; ordinal < chunks.size(); ordinal++) {
            jdbc.update("INSERT INTO document_chunks "
                            + "(document_id, organization_id, ordinal, content, embedding_model, embedding) "
                            + "VALUES (?, ?, ?, ?, ?, CAST(? AS vector(768)))",
                    id, organizationId, ordinal, chunks.get(ordinal), model, vectorLiteral(vectors.get(ordinal)));
        }
        return new IngestResult(find(id, organizationId).orElseThrow(), true);
    }

    public List<KnowledgeDocument> list(UUID organizationId, int page, int size) {
        return jdbc.query("SELECT " + DOCUMENT_COLUMNS
                        + " FROM knowledge_documents WHERE organization_id = ? "
                        + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                this::mapDocument, organizationId, size, page * size);
    }

    public long count(UUID organizationId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM knowledge_documents WHERE organization_id = ?", Long.class, organizationId);
        return count == null ? 0 : count;
    }

    public List<DocumentChunk> chunks(UUID id, UUID organizationId) {
        return jdbc.query("SELECT ordinal, content FROM document_chunks "
                        + "WHERE document_id = ? AND organization_id = ? ORDER BY ordinal",
                (rs, row) -> new DocumentChunk(rs.getInt("ordinal"), rs.getString("content")), id, organizationId);
    }

    @Transactional
    public boolean delete(UUID id, UUID organizationId) {
        return jdbc.update("DELETE FROM knowledge_documents WHERE id = ? AND organization_id = ?",
                id, organizationId) == 1;
    }

    public boolean hasChunks(UUID organizationId, String model) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM document_chunks "
                        + "WHERE organization_id = ? AND embedding_model = ?)",
                Boolean.class, organizationId, model);
        return Boolean.TRUE.equals(exists);
    }

    public List<SearchHit> search(UUID organizationId, String model, double[] query, int topK) {
        String vector = vectorLiteral(query);
        return jdbc.query("""
                        WITH tenant_chunks AS MATERIALIZED (
                            SELECT id, document_id, organization_id, ordinal, content, embedding
                            FROM document_chunks WHERE organization_id = ? AND embedding_model = ?
                        )
                        SELECT tc.document_id, d.title, tc.ordinal, tc.content,
                               1 - (tc.embedding <=> CAST(? AS vector)) AS similarity
                        FROM tenant_chunks tc
                        JOIN knowledge_documents d ON d.id = tc.document_id AND d.organization_id = tc.organization_id
                        ORDER BY tc.embedding <=> CAST(? AS vector), tc.id
                        LIMIT ?
                        """,
                (rs, row) -> new SearchHit(rs.getObject("document_id", UUID.class), rs.getString("title"),
                        rs.getInt("ordinal"), rs.getString("content"), rs.getDouble("similarity")),
                organizationId, model, vector, vector, topK);
    }

    private KnowledgeDocument mapDocument(ResultSet rs, int row) throws SQLException {
        return new KnowledgeDocument(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("embedding_model"), rs.getInt("chunk_count"), rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private String vectorLiteral(double[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) literal.append(',');
            literal.append(Double.toString(vector[i]));
        }
        return literal.append(']').toString();
    }
}
