package com.kjw.tradepilot.order.application.service;

import com.kjw.tradepilot.marketdata.application.port.in.MarketDataStreamQuery;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

@Component
class PaperOrderMatchingEngine {
    private static final Logger log = LoggerFactory.getLogger(PaperOrderMatchingEngine.class);

    private final MarketDataStreamQuery marketDataStream;
    private final PaperOrderService paperOrderService;
    private Disposable subscription;

    PaperOrderMatchingEngine(MarketDataStreamQuery marketDataStream, PaperOrderService paperOrderService) {
        this.marketDataStream = marketDataStream;
        this.paperOrderService = paperOrderService;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        subscription = marketDataStream.stream()
                .concatMap(paperOrderService::match)
                .subscribe(
                        ignored -> { },
                        error -> log.error("Paper order matching stream stopped", error)
                );
    }

    @PreDestroy
    void stop() {
        if (subscription != null) subscription.dispose();
    }
}
