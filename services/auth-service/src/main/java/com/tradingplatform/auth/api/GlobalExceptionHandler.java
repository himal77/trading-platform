package com.tradingplatform.auth.api;

import com.tradingplatform.auth.service.AccountSuspendedException;
import com.tradingplatform.auth.service.EmailAlreadyRegisteredException;
import com.tradingplatform.auth.service.InvalidCredentialsException;
import com.tradingplatform.auth.service.UserNotFoundException;
import com.tradingplatform.auth.token.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail onEmailTaken(EmailAlreadyRegisteredException e) {
        return problem(HttpStatus.CONFLICT, "Email already registered");
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidTokenException.class})
    ProblemDetail onUnauthorized(RuntimeException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AccountSuspendedException.class)
    ProblemDetail onSuspended(AccountSuspendedException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail onNotFound(UserNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailure(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, details);
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
