package com.kjw.tradepilot.marketdata.adapter.in.web;

import com.kjw.tradepilot.marketdata.application.port.in.MarketDataQuery;
import com.kjw.tradepilot.marketdata.application.port.in.MarketDataStreamQuery;
import com.kjw.tradepilot.marketdata.domain.Market;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/market-data")
class MarketDataQueryController {
    private final MarketDataQuery marketDataQuery;
    private final MarketDataStreamQuery marketDataStreamQuery;

    MarketDataQueryController(MarketDataQuery marketDataQuery, MarketDataStreamQuery marketDataStreamQuery) {
        this.marketDataQuery = marketDataQuery;
        this.marketDataStreamQuery = marketDataStreamQuery;
    }

    @GetMapping("/quotes/{symbol}")
    Mono<ResponseEntity<MarketTickResponse>> getLatest(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "KRX") Market market
    ) {
        return marketDataQuery.getLatest(market, symbol)
                .map(MarketTickResponse::from)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<MarketTickResponse>> stream(
            @RequestParam(required = false, defaultValue = "") Set<String> symbols
    ) {
        Set<String> normalizedSymbols = symbols.stream()
                .filter(symbol -> !symbol.isBlank())
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        return marketDataStreamQuery.stream()
                .filter(tick -> normalizedSymbols.isEmpty() || normalizedSymbols.contains(tick.symbol()))
                .map(MarketTickResponse::from)
                .map(response -> ServerSentEvent.builder(response)
                        .event("market-tick")
                        .id(response.market() + ":" + response.symbol() + ":" + response.tradedAt())
                        .build());
    }
}
