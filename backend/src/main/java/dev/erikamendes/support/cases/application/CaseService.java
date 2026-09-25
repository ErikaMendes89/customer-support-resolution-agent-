package dev.erikamendes.support.cases.application;

import dev.erikamendes.support.cases.domain.CaseEvent;
import dev.erikamendes.support.cases.domain.CaseStatus;
import dev.erikamendes.support.cases.domain.SupportCase;
import dev.erikamendes.support.cases.infrastructure.CaseRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {
    private final CaseRepository repository;

    public CaseService(CaseRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SupportCase create(UUID organizationId, String actor, String title, String description) {
        UUID id = UUID.randomUUID();
        repository.insert(id, organizationId, title.trim(), description.trim(), actor);
        repository.appendEvent(id, organizationId, "CREATED", null, CaseStatus.OPEN, null, actor);
        return repository.find(id, organizationId).orElseThrow(CaseNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public CasePage list(UUID organizationId, int page, int size) {
        return new CasePage(repository.list(organizationId, page, size),
                repository.count(organizationId), page, size);
    }

    @Transactional(readOnly = true)
    public CaseDetail get(UUID id, UUID organizationId) {
        SupportCase supportCase = repository.find(id, organizationId).orElseThrow(CaseNotFoundException::new);
        return new CaseDetail(supportCase, repository.events(id, organizationId));
    }

    @Transactional
    public CaseDetail changeStatus(UUID id, UUID organizationId, String actor,
                                   CaseStatus target, String note) {
        SupportCase current = repository.findForUpdate(id, organizationId)
                .orElseThrow(CaseNotFoundException::new);
        if (!current.status().canTransitionTo(target)) {
            throw new InvalidCaseTransitionException();
        }
        String normalizedNote = note == null || note.isBlank() ? null : note.trim();
        repository.updateStatus(id, organizationId, target);
        repository.appendEvent(id, organizationId, "STATUS_CHANGED", current.status(), target,
                normalizedNote, actor);
        return get(id, organizationId);
    }

    public record CasePage(List<SupportCase> items, long total, int page, int size) { }
    public record CaseDetail(SupportCase supportCase, List<CaseEvent> events) { }
}
