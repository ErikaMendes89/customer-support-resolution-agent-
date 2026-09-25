package dev.erikamendes.support.cases.api;

import dev.erikamendes.support.cases.application.CaseService;
import dev.erikamendes.support.cases.domain.CaseEvent;
import dev.erikamendes.support.cases.domain.CaseStatus;
import dev.erikamendes.support.cases.domain.SupportCase;
import dev.erikamendes.support.identity.SupportUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {
    private final CaseService service;

    public CaseController(CaseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CaseResponse> create(@AuthenticationPrincipal SupportUser user,
                                                @Valid @RequestBody CreateCase request) {
        if (request.title().trim().length() < 3 || request.description().trim().length() < 10) {
            throw new InvalidCaseInputException();
        }
        SupportCase created = service.create(user.organizationId(), user.getUsername(),
                request.title(), request.description());
        return ResponseEntity.created(URI.create("/api/v1/cases/" + created.id()))
                .body(CaseResponse.from(created));
    }

    @GetMapping
    public CasePageResponse list(@AuthenticationPrincipal SupportUser user,
                                 @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        var result = service.list(user.organizationId(), page, size);
        return new CasePageResponse(result.items().stream().map(CaseResponse::from).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    public CaseDetailResponse get(@AuthenticationPrincipal SupportUser user, @PathVariable UUID id) {
        return CaseDetailResponse.from(service.get(id, user.organizationId()));
    }

    @PatchMapping("/{id}/status")
    public CaseDetailResponse changeStatus(@AuthenticationPrincipal SupportUser user,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody ChangeStatus request) {
        return CaseDetailResponse.from(service.changeStatus(id, user.organizationId(), user.getUsername(),
                request.status(), request.note()));
    }

    public record CreateCase(@NotBlank @Size(max = 160) String title,
                             @NotBlank @Size(max = 5000) String description) { }
    public record ChangeStatus(@NotNull CaseStatus status, @Size(max = 2000) String note) { }
    public record CasePageResponse(List<CaseResponse> items, long total, int page, int size) { }
    public record CaseResponse(UUID id, String title, String description, CaseStatus status,
                               List<CaseStatus> allowedTransitions, String createdBy,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        static CaseResponse from(SupportCase supportCase) {
            return new CaseResponse(supportCase.id(), supportCase.title(), supportCase.description(),
                    supportCase.status(), supportCase.status().allowedTransitions(),
                    supportCase.createdBy(), supportCase.createdAt(), supportCase.updatedAt());
        }
    }
    public record CaseDetailResponse(CaseResponse supportCase, List<CaseEvent> events) {
        static CaseDetailResponse from(CaseService.CaseDetail detail) {
            return new CaseDetailResponse(CaseResponse.from(detail.supportCase()), detail.events());
        }
    }
}
