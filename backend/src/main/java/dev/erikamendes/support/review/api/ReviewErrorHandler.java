package dev.erikamendes.support.review.api;

import dev.erikamendes.support.review.application.InvalidReviewException;
import dev.erikamendes.support.review.application.ReviewConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReviewErrorHandler {
    @ExceptionHandler(InvalidReviewException.class)
    ProblemDetail invalid() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Decisão inválida: edição exige texto diferente, fonte citada e justificativa; rejeição exige justificativa.");
    }

    @ExceptionHandler(ReviewConflictException.class)
    ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A proposta já foi revisada ou o caso não aceita revisão.");
    }
}
