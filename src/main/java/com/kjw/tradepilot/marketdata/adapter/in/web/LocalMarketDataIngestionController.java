package com.kjw.tradepilot.marketdata.adapter.in.web;

import com.kjw.tradepilot.marketdata.application.port.in.IngestMarketTickUseCase;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

@Profile("local | simulation")
@RestController
@RequestMapping("/api/v1/market-data/ticks")
class LocalMarketDataIngestionController {
    private final IngestMarketTickUseCase ingestMarketTick;

    LocalMarketDataIngestionController(IngestMarketTickUseCase ingestMarketTick) {
        this.ingestMarketTick = ingestMarketTick;
    }

    @PostMapping
    Mono<ResponseEntity<MarketTickResponse>> ingest(@Valid @RequestBody MarketTickRequest request) {
        return ingestMarketTick.ingest(request.toDomain())
                .map(MarketTickResponse::from)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/v1/market-data/quotes/" + response.symbol()))
                        .body(response));
    }
}
