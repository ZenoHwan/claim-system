package com.claims.service.common;

import org.springframework.http.HttpStatus;

/** A failure the caller caused and can be told about. Mapped to a problem-details response. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String field;

    private ApiException(HttpStatus status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, null, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, null, message);
    }

    /** Also used when the caller is not allowed to know the thing exists. */
    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, null, what + " not found");
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, null, message);
    }

    /** A rule that bean validation cannot express, reported against one field. */
    public static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, field, message);
    }

    public static ApiException tooLarge(String message) {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE, null, message);
    }

    public HttpStatus status() {
        return status;
    }

    public String field() {
        return field;
    }
}
