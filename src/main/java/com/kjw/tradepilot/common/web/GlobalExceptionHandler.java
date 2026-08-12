package com.kjw.tradepilot.common.web;

import com.kjw.tradepilot.watchlist.application.WatchlistItemAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(WatchlistItemAlreadyExistsException.class)
    ResponseEntity<ApiError> handleWatchlistConflict(WatchlistItemAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiError("WATCHLIST_ITEM_EXISTS", exception.getMessage(), List.of(), Instant.now())
        );
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ApiError> handleValidation(WebExchangeBindException exception) {
        List<String> details = exception.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(
                new ApiError("VALIDATION_ERROR", "Request validation failed", details, Instant.now())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiError("INVALID_ARGUMENT", exception.getMessage(), List.of(), Instant.now())
        );
    }
}
