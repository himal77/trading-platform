package com.tradingplatform.wallet.api;

import com.tradingplatform.wallet.domain.InsufficientFundsException;
import com.tradingplatform.wallet.service.DuplicateTransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail onForbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /**
     * 422 rather than 400: the request was well-formed and understood, it just cannot be satisfied
     * given the current balance.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail onInsufficientFunds(InsufficientFundsException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /**
     * 409 tells the caller the operation was already applied — so it must not retry, but it also
     * has not failed. Callers should treat this as "already done".
     */
    @ExceptionHandler(DuplicateTransactionException.class)
    ProblemDetail onDuplicate(DuplicateTransactionException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    /** A missing or unparseable X-User-Id means no identity was supplied, not a malformed request. */
    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail onMissingIdentity(Exception e) {
        return problem(HttpStatus.UNAUTHORIZED, "Missing or invalid caller identity");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
