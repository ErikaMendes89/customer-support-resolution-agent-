package dev.erikamendes.support.knowledge.api;

import dev.erikamendes.support.identity.SupportUser;
import dev.erikamendes.support.knowledge.application.KnowledgeService;
import dev.erikamendes.support.knowledge.domain.DocumentChunk;
import dev.erikamendes.support.knowledge.domain.KnowledgeDocument;
import dev.erikamendes.support.knowledge.domain.SearchHit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) { this.service = service; }

    @PostMapping("/documents")
    public ResponseEntity<KnowledgeDocument> ingest(@AuthenticationPrincipal SupportUser user,
                                                    @Valid @RequestBody IngestRequest request) {
        var result = service.ingest(user.organizationId(), user.getUsername(), request.title(), request.content());
        URI location = URI.create("/api/v1/documents/" + result.document().id());
        return result.created() ? ResponseEntity.created(location).body(result.document())
                : ResponseEntity.ok().location(location).body(result.document());
    }

    @GetMapping("/documents")
    public DocumentPage list(@AuthenticationPrincipal SupportUser user,
                             @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                             @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        var result = service.list(user.organizationId(), page, size);
        return new DocumentPage(result.items(), result.total(), result.page(), result.size());
    }

    @GetMapping("/documents/{id}")
    public DocumentDetail get(@AuthenticationPrincipal SupportUser user, @PathVariable UUID id) {
        var detail = service.get(id, user.organizationId());
        return new DocumentDetail(detail.document(), detail.chunks());
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SupportUser user, @PathVariable UUID id) {
        service.delete(id, user.organizationId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/knowledge/search")
    public List<SearchHit> search(@AuthenticationPrincipal SupportUser user,
                                  @Valid @RequestBody SearchRequest request) {
        return service.search(user.organizationId(), request.query(), request.topK());
    }

    public record IngestRequest(@NotBlank @Size(max = 160) String title,
                                @NotBlank @Size(max = 12000) String content) { }
    public record SearchRequest(@NotBlank @Size(max = 500) String query,
                                @Min(1) @Max(10) int topK) { }
    public record DocumentPage(List<KnowledgeDocument> items, long total, int page, int size) { }
    public record DocumentDetail(KnowledgeDocument document, List<DocumentChunk> chunks) { }
}
