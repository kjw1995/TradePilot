package com.kjw.tradepilot.order.adapter.in.web;

import com.kjw.tradepilot.order.application.port.in.PaperOrderUseCase;
import com.kjw.tradepilot.order.application.port.in.PlaceOrderCommand;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/orders")
class PaperOrderController {
    private final PaperOrderUseCase paperOrderUseCase;

    PaperOrderController(PaperOrderUseCase paperOrderUseCase) {
        this.paperOrderUseCase = paperOrderUseCase;
    }

    @PostMapping
    Mono<ResponseEntity<PaperOrderResponse>> place(
            @PathVariable String accountId,
            @Valid @RequestBody PlacePaperOrderRequest request
    ) {
        PlaceOrderCommand command = new PlaceOrderCommand(
                accountId, request.market(), request.symbol(), request.side(), request.orderType(),
                request.quantity(), request.limitPrice(), request.idempotencyKey()
        );
        return paperOrderUseCase.place(command)
                .map(PaperOrderResponse::from)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/v1/accounts/" + accountId + "/orders/" + response.orderId()))
                        .body(response));
    }

    @GetMapping
    Flux<PaperOrderResponse> getOrders(@PathVariable String accountId) {
        return paperOrderUseCase.getOrders(accountId).map(PaperOrderResponse::from);
    }

    @GetMapping("/executions")
    Flux<TradeExecutionResponse> getExecutions(@PathVariable String accountId) {
        return paperOrderUseCase.getExecutions(accountId).map(TradeExecutionResponse::from);
    }

    @DeleteMapping("/{orderId}")
    Mono<PaperOrderResponse> cancel(@PathVariable String accountId, @PathVariable UUID orderId) {
        return paperOrderUseCase.cancel(accountId, orderId).map(PaperOrderResponse::from);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<PaperOrderResponse>> stream(@PathVariable String accountId) {
        return paperOrderUseCase.stream(accountId)
                .map(PaperOrderResponse::from)
                .map(response -> ServerSentEvent.builder(response)
                        .id(response.orderId() + ":" + response.updatedAt())
                        .event("order-updated")
                        .build());
    }
}
