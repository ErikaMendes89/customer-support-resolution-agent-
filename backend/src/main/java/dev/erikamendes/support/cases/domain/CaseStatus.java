package dev.erikamendes.support.cases.domain;

import java.util.List;

public enum CaseStatus {
    OPEN,
    IN_PROGRESS,
    NEEDS_INFORMATION,
    RESOLVED,
    CLOSED;

    public List<CaseStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> List.of(IN_PROGRESS, NEEDS_INFORMATION, RESOLVED);
            case IN_PROGRESS -> List.of(NEEDS_INFORMATION, RESOLVED);
            case NEEDS_INFORMATION -> List.of(IN_PROGRESS);
            case RESOLVED -> List.of(CLOSED);
            case CLOSED -> List.of();
        };
    }

    public boolean canTransitionTo(CaseStatus target) {
        return allowedTransitions().contains(target);
    }
}
