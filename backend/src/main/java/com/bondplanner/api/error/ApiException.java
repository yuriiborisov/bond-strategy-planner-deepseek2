package com.bondplanner.api.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient List<String> suggestions;

    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, List.of());
    }

    public ApiException(HttpStatus status, String code, String detail, List<String> suggestions) {
        super(detail);
        this.status = status;
        this.code = code;
        this.suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", detail);
    }

    public static ApiException noCandidates(String detail, List<String> suggestions) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_CANDIDATES", detail, suggestions);
    }

    public static ApiException inconsistent(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INCONSISTENT_PARAMS", detail);
    }
}
