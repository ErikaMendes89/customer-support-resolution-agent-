package dev.erikamendes.support.review.api;

import dev.erikamendes.support.identity.SupportUser;
import dev.erikamendes.support.review.application.ReviewService;
import dev.erikamendes.support.review.domain.ProposalReview;
import dev.erikamendes.support.review.domain.ProposalReview.Decision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}")
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service) { this.service = service; }

    @PostMapping("/proposals/{proposalId}/review")
    public ResponseEntity<ProposalReview> decide(@AuthenticationPrincipal SupportUser user,
            @PathVariable UUID caseId, @PathVariable UUID proposalId, @Valid @RequestBody ReviewRequest request) {
        var review = service.decide(caseId, proposalId, user.organizationId(), user.getUsername(),
                request.decision(), request.editedAnswer(), request.note());
        return ResponseEntity.created(URI.create("/api/v1/cases/" + caseId + "/proposals/" + proposalId + "/review"))
                .body(review);
    }

    @GetMapping("/proposals/{proposalId}/review")
    public ProposalReview get(@AuthenticationPrincipal SupportUser user,
            @PathVariable UUID caseId, @PathVariable UUID proposalId) {
        return service.get(caseId, proposalId, user.organizationId());
    }

    @GetMapping("/reviews")
    public List<ProposalReview> list(@AuthenticationPrincipal SupportUser user, @PathVariable UUID caseId) {
        return service.list(caseId, user.organizationId());
    }

    public record ReviewRequest(@NotNull Decision decision, @Size(max = 3000) String editedAnswer,
                                @Size(max = 2000) String note) { }
}
