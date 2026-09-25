package dev.erikamendes.support.knowledge.api;

import dev.erikamendes.support.knowledge.application.DocumentNotFoundException;
import dev.erikamendes.support.knowledge.application.EmbeddingUnavailableException;
import dev.erikamendes.support.knowledge.application.InvalidDocumentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class KnowledgeErrorHandler {
    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Documento não encontrado.");
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ProblemDetail invalid() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Texto muito curto ou inválido.");
    }

    @ExceptionHandler(EmbeddingUnavailableException.class)
    ProblemDetail embeddingUnavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível gerar os embeddings. Tente novamente mais tarde.");
    }
}
