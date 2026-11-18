package com.claims.service.common;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error leaves the service as RFC 9457 problem details; validation errors add an "errors" list. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    record FieldError(String field, String message) {}

    @ExceptionHandler(ApiException.class)
    ProblemDetail handle(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        if (ex.field() != null) {
            problem.setProperty("errors", List.of(new FieldError(ex.field(), ex.getMessage())));
        }
        return problem;
    }

    /** Two requests changed the same claim; the loser is told to reload. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleLostRace() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The claim was changed by someone else. Reload and try again.");
    }

    /** A database constraint caught what the code's own check missed, typically two requests at once. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConstraint() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data. Reload and try again.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList());
        return ResponseEntity.status(status).body(problem);
    }
}
