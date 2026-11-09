package com.example.shortener.web;

import com.example.shortener.domain.CodeUnavailableException;
import com.example.shortener.domain.InvalidUrlException;
import com.example.shortener.domain.LinkNotFoundException;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<Map<String, String>> invalidUrl(InvalidUrlException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body(isHead(request) ? null
                        : Map.of("code", "INVALID_URL", "message", exception.getMessage(), "field", "url"));
    }

    @ExceptionHandler({InvalidJsonException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidJson(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Provide one valid JSON object containing a url field.", request);
    }

    @ExceptionHandler({LinkNotFoundException.class, NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Map<String, String>> notFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Short code or resource not found.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> unsupportedMediaType(HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "URL creation requires application/json.", request);
    }

    @ExceptionHandler(CodeUnavailableException.class)
    public ResponseEntity<Map<String, String>> codeUnavailable(HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "CODE_UNAVAILABLE", "Unable to generate a unique short code. Retry the request.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internalError(HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected server error occurred.", request);
    }

    private static boolean isHead(HttpServletRequest request) {
        return "HEAD".equals(request.getMethod());
    }

    private static ResponseEntity<Map<String, String>> error(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(isHead(request) ? null : Map.of("code", code, "message", message));
    }
}
