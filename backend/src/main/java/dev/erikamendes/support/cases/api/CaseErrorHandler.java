package dev.erikamendes.support.cases.api;

import dev.erikamendes.support.cases.application.CaseNotFoundException;
import dev.erikamendes.support.cases.application.InvalidCaseTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class CaseErrorHandler {
    @ExceptionHandler(CaseNotFoundException.class)
    ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Caso não encontrado.");
    }

    @ExceptionHandler(InvalidCaseTransitionException.class)
    ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Transição de estado inválida.");
    }

    @ExceptionHandler(InvalidCaseInputException.class)
    ProblemDetail invalidInput() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Título ou descrição muito curtos.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidParameter() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parâmetro inválido.");
    }
}
