package com.kjw.tradepilot.alert.adapter.in.web;

import com.kjw.tradepilot.alert.application.port.in.CreatePriceAlertCommand;
import com.kjw.tradepilot.alert.application.port.in.PriceAlertUseCase;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/accounts/{accountId}/price-alerts")
class PriceAlertController {
    private final PriceAlertUseCase priceAlertUseCase;

    PriceAlertController(PriceAlertUseCase priceAlertUseCase) {
        this.priceAlertUseCase = priceAlertUseCase;
    }

    @PostMapping
    Mono<ResponseEntity<PriceAlertResponse>> create(
            @PathVariable String accountId,
            @Valid @RequestBody CreatePriceAlertRequest request
    ) {
        CreatePriceAlertCommand command = new CreatePriceAlertCommand(
                accountId, request.market(), request.symbol(), request.condition(), request.targetPrice()
        );
        return priceAlertUseCase.create(command)
                .map(PriceAlertResponse::from)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/v1/accounts/" + accountId + "/price-alerts/" + response.alertId()))
                        .body(response));
    }

    @GetMapping
    Flux<PriceAlertResponse> getAlerts(@PathVariable String accountId) {
        return priceAlertUseCase.getAlerts(accountId).map(PriceAlertResponse::from);
    }

    @DeleteMapping("/{alertId}")
    Mono<ResponseEntity<Void>> delete(@PathVariable String accountId, @PathVariable UUID alertId) {
        return priceAlertUseCase.delete(accountId, alertId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PatchMapping("/{alertId}/reactivate")
    Mono<PriceAlertResponse> reactivate(@PathVariable String accountId, @PathVariable UUID alertId) {
        return priceAlertUseCase.reactivate(accountId, alertId).map(PriceAlertResponse::from);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<PriceAlertResponse>> stream(@PathVariable String accountId) {
        return priceAlertUseCase.stream(accountId)
                .map(PriceAlertResponse::from)
                .map(response -> ServerSentEvent.builder(response)
                        .id(response.alertId() + ":" + response.updatedAt())
                        .event("price-alert-updated")
                        .build());
    }
}
