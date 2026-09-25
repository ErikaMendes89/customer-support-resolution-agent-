package dev.erikamendes.support.review.application;

import dev.erikamendes.support.cases.application.CaseNotFoundException;
import dev.erikamendes.support.cases.domain.CaseStatus;
import dev.erikamendes.support.cases.infrastructure.CaseRepository;
import dev.erikamendes.support.proposals.infrastructure.ProposalRepository;
import dev.erikamendes.support.review.domain.ProposalReview;
import dev.erikamendes.support.review.domain.ProposalReview.Decision;
import dev.erikamendes.support.review.infrastructure.ReviewRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private static final Pattern CITATION = Pattern.compile("\\[S[0-9]+\\]");
    private final CaseRepository cases;
    private final ProposalRepository proposals;
    private final ReviewRepository reviews;

    public ReviewService(CaseRepository cases, ProposalRepository proposals, ReviewRepository reviews) {
        this.cases = cases; this.proposals = proposals; this.reviews = reviews;
    }

    @Transactional
    public ProposalReview decide(UUID caseId, UUID proposalId, UUID org, String actor,
                                 Decision decision, String editedAnswer, String note) {
        var current = cases.findForUpdate(caseId, org).orElseThrow(CaseNotFoundException::new);
        String status = reviews.lockProposal(proposalId, caseId, org).orElseThrow(CaseNotFoundException::new);
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        String cleanAnswer = editedAnswer == null || editedAnswer.isBlank() ? null : editedAnswer.trim();
        var previous = reviews.find(proposalId, caseId, org);
        if (previous.isPresent()) {
            ProposalReview saved = previous.get();
            boolean same = saved.decision() == decision && saved.reviewedBy().equals(actor)
                    && java.util.Objects.equals(saved.note(), cleanNote)
                    && (decision == Decision.EDITED
                        ? java.util.Objects.equals(saved.finalAnswer(), cleanAnswer) : cleanAnswer == null);
            if (same) return saved;
            throw new ReviewConflictException();
        }
        if (current.status() == CaseStatus.RESOLVED || current.status() == CaseStatus.CLOSED)
            throw new ReviewConflictException();
        String finalAnswer = null;
        if (decision == Decision.REJECTED) {
            if (cleanNote == null || cleanAnswer != null) throw new InvalidReviewException();
        } else {
            if (!status.equals("READY_FOR_REVIEW")) throw new ReviewConflictException();
            var proposal = proposals.find(proposalId, caseId, org).orElseThrow(CaseNotFoundException::new);
            if (decision == Decision.APPROVED) {
                if (cleanAnswer != null) throw new InvalidReviewException();
                finalAnswer = proposal.answer();
            } else {
                if (cleanNote == null || cleanAnswer == null || cleanAnswer.equals(proposal.answer())
                        || cleanAnswer.length() > 3000) throw new InvalidReviewException();
                Set<String> known = new HashSet<>();
                proposal.sources().forEach(source -> known.add(source.key()));
                Set<String> cited = new HashSet<>();
                Matcher matcher = CITATION.matcher(cleanAnswer);
                while (matcher.find()) cited.add(matcher.group().substring(1, matcher.group().length() - 1));
                if (cited.isEmpty() || !known.containsAll(cited)
                        || cleanAnswer.replaceAll("\\[S[0-9]+\\]", "").matches("(?s).*\\[[^\\]]*S[0-9]+[^\\]]*\\].*"))
                    throw new InvalidReviewException();
                finalAnswer = cleanAnswer;
            }
        }
        return reviews.insert(proposalId, caseId, org, decision, finalAnswer, cleanNote, actor);
    }

    @Transactional(readOnly = true)
    public List<ProposalReview> list(UUID caseId, UUID org) {
        cases.find(caseId, org).orElseThrow(CaseNotFoundException::new);
        return reviews.list(caseId, org);
    }

    @Transactional(readOnly = true)
    public ProposalReview get(UUID caseId, UUID proposalId, UUID org) {
        cases.find(caseId, org).orElseThrow(CaseNotFoundException::new);
        proposals.find(proposalId, caseId, org).orElseThrow(CaseNotFoundException::new);
        return reviews.find(proposalId, caseId, org).orElseThrow(CaseNotFoundException::new);
    }
}
