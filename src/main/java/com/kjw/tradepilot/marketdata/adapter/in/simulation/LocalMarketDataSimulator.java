package com.kjw.tradepilot.marketdata.adapter.in.simulation;

import com.kjw.tradepilot.marketdata.application.port.in.IngestMarketTickUseCase;
import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Profile("simulation")
@Component
class LocalMarketDataSimulator {
    private static final Logger log = LoggerFactory.getLogger(LocalMarketDataSimulator.class);
    private static final String[] SYMBOLS = {"005930", "000660"};

    private final IngestMarketTickUseCase ingestMarketTick;

    LocalMarketDataSimulator(IngestMarketTickUseCase ingestMarketTick) {
        this.ingestMarketTick = ingestMarketTick;
    }

    @Scheduled(fixedDelayString = "${tradepilot.market-data.simulation-fixed-delay:1s}")
    void emitTick() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String symbol = SYMBOLS[random.nextInt(SYMBOLS.length)];
        long basePrice = symbol.equals("005930") ? 80_000L : 190_000L;
        MarketTick tick = new MarketTick(
                symbol,
                Market.KRX,
                BigDecimal.valueOf(basePrice + random.nextLong(-1_000, 1_001)),
                random.nextLong(1, 2_001),
                Instant.now(),
                Instant.now(),
                "SIMULATION"
        );

        ingestMarketTick.ingest(tick)
                .doOnError(error -> log.warn("Unable to persist simulated market tick", error))
                .subscribe();
    }
}
