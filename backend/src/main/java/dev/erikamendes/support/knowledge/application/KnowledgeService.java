package dev.erikamendes.support.knowledge.application;

import dev.erikamendes.support.knowledge.domain.DocumentChunk;
import dev.erikamendes.support.knowledge.domain.KnowledgeDocument;
import dev.erikamendes.support.knowledge.domain.SearchHit;
import dev.erikamendes.support.knowledge.domain.TextChunker;
import dev.erikamendes.support.knowledge.infrastructure.DocumentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {
    private static final int DIMENSIONS = 768;
    private final DocumentRepository repository;
    private final EmbeddingClient embeddings;
    private final TextChunker chunker;

    public KnowledgeService(DocumentRepository repository, EmbeddingClient embeddings, TextChunker chunker) {
        this.repository = repository;
        this.embeddings = embeddings;
        this.chunker = chunker;
    }

    public IngestResult ingest(UUID organizationId, String actor, String title, String content) {
        String normalizedTitle = title.trim();
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalizedTitle.length() < 3 || normalized.codePointCount(0, normalized.length()) < 20) {
            throw new InvalidDocumentException();
        }
        String hash = sha256(normalized);
        String model = embeddings.model();
        var existing = repository.findByHash(organizationId, hash, model);
        if (existing.isPresent()) return new IngestResult(existing.get(), false);

        List<String> chunks = chunker.split(normalized);
        if (chunks.isEmpty()) throw new InvalidDocumentException();
        // Network I/O occurs before the database transaction; partial documents never become visible.
        List<double[]> vectors = embeddings.embed(chunks);
        validateVectors(vectors, chunks.size());
        return repository.insert(organizationId, actor, normalizedTitle, hash, model, chunks, vectors);
    }

    public Page list(UUID organizationId, int page, int size) {
        return new Page(repository.list(organizationId, page, size), repository.count(organizationId), page, size);
    }

    public Detail get(UUID id, UUID organizationId) {
        KnowledgeDocument document = repository.find(id, organizationId)
                .orElseThrow(DocumentNotFoundException::new);
        return new Detail(document, repository.chunks(id, organizationId));
    }

    public void delete(UUID id, UUID organizationId) {
        if (!repository.delete(id, organizationId)) throw new DocumentNotFoundException();
    }

    public List<SearchHit> search(UUID organizationId, String query, int topK) {
        String normalized = query.trim();
        if (normalized.codePointCount(0, normalized.length()) < 3) throw new InvalidDocumentException();
        String model = embeddings.model();
        // Avoid a model call when no searchable content exists in this organization.
        if (!repository.hasChunks(organizationId, model)) return List.of();
        List<double[]> vectors = embeddings.embed(List.of(normalized));
        validateVectors(vectors, 1);
        return repository.search(organizationId, model, vectors.get(0), topK);
    }

    private void validateVectors(List<double[]> vectors, int count) {
        if (vectors == null || vectors.size() != count) throw new EmbeddingUnavailableException();
        for (double[] vector : vectors) {
            if (vector == null || vector.length != DIMENSIONS) throw new EmbeddingUnavailableException();
            double norm = 0;
            for (double value : vector) {
                if (!Double.isFinite(value)) throw new EmbeddingUnavailableException();
                norm += value * value;
            }
            if (norm == 0 || !Double.isFinite(norm)) throw new EmbeddingUnavailableException();
        }
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record IngestResult(KnowledgeDocument document, boolean created) { }
    public record Page(List<KnowledgeDocument> items, long total, int page, int size) { }
    public record Detail(KnowledgeDocument document, List<DocumentChunk> chunks) { }
}
