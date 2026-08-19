package com.kjw.tradepilot.alert.application.service;

import com.kjw.tradepilot.marketdata.application.port.in.MarketDataStreamQuery;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Component
class PriceAlertEvaluationEngine {
    private static final Logger log = LoggerFactory.getLogger(PriceAlertEvaluationEngine.class);

    private final MarketDataStreamQuery marketDataStream;
    private final PriceAlertService priceAlertService;
    private Disposable subscription;

    PriceAlertEvaluationEngine(MarketDataStreamQuery marketDataStream, PriceAlertService priceAlertService) {
        this.marketDataStream = marketDataStream;
        this.priceAlertService = priceAlertService;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        subscription = marketDataStream.stream()
                .concatMap(tick -> priceAlertService.evaluate(tick).onErrorResume(error -> {
                    log.warn("Unable to evaluate price alerts: market={}, symbol={}", tick.market(), tick.symbol(), error);
                    return Mono.empty();
                }))
                .subscribe(
                        ignored -> { },
                        error -> log.error("Price alert evaluation stream stopped", error)
                );
    }

    @PreDestroy
    void stop() {
        if (subscription != null) subscription.dispose();
    }
}
