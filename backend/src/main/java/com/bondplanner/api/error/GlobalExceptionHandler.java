package com.bondplanner.api.error;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setTitle(titleFor(ex.getCode()));
        pd.setType(URI.create("https://bondplanner.local/errors/" + ex.getCode().toLowerCase().replace('_', '-')));
        pd.setProperty("code", ex.getCode());
        pd.setProperty("instance", "request");
        pd.setProperty("timestamp", Instant.now().toString());
        if (!ex.getSuggestions().isEmpty()) {
            pd.setProperty("suggestions", ex.getSuggestions());
        }
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Параметры запроса не прошли валидацию");
        pd.setTitle("Validation error");
        pd.setProperty("code", "VALIDATION_ERROR");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Validation error");
        pd.setProperty("code", "VALIDATION_ERROR");
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Некорректный JSON в запросе");
        pd.setTitle("Malformed request");
        pd.setProperty("code", "VALIDATION_ERROR");
        return pd;
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingRequestHeaderException.class})
    public ProblemDetail handleType(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Malformed request");
        pd.setProperty("code", "VALIDATION_ERROR");
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Недостаточно прав");
        pd.setTitle("Forbidden");
        pd.setProperty("code", "FORBIDDEN");
        return pd;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuth(AuthenticationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Требуется авторизация");
        pd.setTitle("Unauthorized");
        pd.setProperty("code", "UNAUTHORIZED");
        return pd;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Ресурс не найден");
        pd.setTitle("Not found");
        pd.setProperty("code", "NOT_FOUND");
        return pd;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUpload(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Файл слишком большой");
        pd.setTitle("Payload too large");
        pd.setProperty("code", "PAYLOAD_TOO_LARGE");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
        pd.setTitle("Internal error");
        pd.setProperty("code", "INTERNAL_ERROR");
        return pd;
    }

    private String titleFor(String code) {
        return switch (code) {
            case "NO_CANDIDATES" -> "No bonds match filters";
            case "INCONSISTENT_PARAMS" -> "Inconsistent parameters";
            case "NO_RATING" -> "Rating required";
            case "ASSET_CHECK_FAILED" -> "Asset checks failed";
            case "DATA_STALE" -> "Data is stale";
            case "LLM_UNAVAILABLE" -> "LLM is unavailable";
            case "AI_QUOTA_EXCEEDED" -> "AI quota exceeded";
            case "CONSENT_REQUIRED" -> "Consent required";
            case "EXCHANGE_UNAVAILABLE" -> "Exchange unavailable";
            case "UNAUTHORIZED" -> "Unauthorized";
            case "FORBIDDEN" -> "Forbidden";
            case "CONFLICT" -> "Conflict";
            default -> "Request failed";
        };
    }
}
