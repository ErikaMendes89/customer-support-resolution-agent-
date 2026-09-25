package dev.erikamendes.support.proposals.api;

import dev.erikamendes.support.proposals.application.GenerationUnavailableException;
import dev.erikamendes.support.proposals.application.ProposalConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProposalErrorHandler {
    @ExceptionHandler(ProposalConflictException.class)
    ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "O caso ou suas fontes mudaram. Atualize e tente novamente.");
    }

    @ExceptionHandler(GenerationUnavailableException.class)
    ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Geração indisponível. Tente novamente mais tarde.");
    }
}
