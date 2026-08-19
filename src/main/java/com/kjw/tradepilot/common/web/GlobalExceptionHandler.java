package com.kjw.tradepilot.common.web;

import com.kjw.tradepilot.alert.application.PriceAlertLimitExceededException;
import com.kjw.tradepilot.alert.application.PriceAlertNotFoundException;
import com.kjw.tradepilot.alert.application.PriceAlertStateException;
import com.kjw.tradepilot.watchlist.application.WatchlistItemAlreadyExistsException;
import com.kjw.tradepilot.order.application.OrderNotCancelableException;
import com.kjw.tradepilot.order.application.OrderRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(PriceAlertLimitExceededException.class)
    ResponseEntity<ApiError> handlePriceAlertLimit(PriceAlertLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                new ApiError("PRICE_ALERT_LIMIT_EXCEEDED", exception.getMessage(), List.of(), Instant.now())
        );
    }

    @ExceptionHandler(PriceAlertNotFoundException.class)
    ResponseEntity<ApiError> handlePriceAlertNotFound(PriceAlertNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ApiError("PRICE_ALERT_NOT_FOUND", exception.getMessage(), List.of(), Instant.now())
        );
    }

    @ExceptionHandler(PriceAlertStateException.class)
    ResponseEntity<ApiError> handlePriceAlertState(PriceAlertStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiError("PRICE_ALERT_INVALID_STATE", exception.getMessage(), List.of(), Instant.now())
        );
    }

    @ExceptionHandler(OrderRejectedException.class)
    ResponseEntity<ApiError> handleOrderRejected(OrderRejectedException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                new ApiError("ORDER_REJECTED", exception.getMessage(), List.of(), Instant.now())
        );
    }

    @ExceptionHandler(OrderNotCancelableException.class)
    ResponseEntity<ApiError> handleOrderNotCancelable(OrderNotCancelableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiError("ORDER_NOT_CANCELABLE", exception.getMessage(), List.of(), Instant.now())
        );
    }

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
