package dev.erikamendes.support.proposals.api;

import dev.erikamendes.support.identity.SupportUser;
import dev.erikamendes.support.proposals.application.ProposalService;
import dev.erikamendes.support.proposals.domain.ResolutionProposal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/proposals")
public class ProposalController {
    private final ProposalService service;
    public ProposalController(ProposalService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ResolutionProposal> generate(@AuthenticationPrincipal SupportUser user, @PathVariable UUID caseId) {
        ResolutionProposal proposal = service.generate(caseId, user.organizationId(), user.getUsername());
        return ResponseEntity.created(URI.create("/api/v1/cases/" + caseId + "/proposals/" + proposal.id())).body(proposal);
    }

    @GetMapping
    public List<ResolutionProposal> list(@AuthenticationPrincipal SupportUser user, @PathVariable UUID caseId) {
        return service.list(caseId, user.organizationId());
    }

    @GetMapping("/{proposalId}")
    public ResolutionProposal get(@AuthenticationPrincipal SupportUser user, @PathVariable UUID caseId,
                                  @PathVariable UUID proposalId) {
        return service.get(caseId, proposalId, user.organizationId());
    }
}
