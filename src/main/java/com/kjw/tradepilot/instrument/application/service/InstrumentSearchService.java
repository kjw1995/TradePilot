package com.kjw.tradepilot.instrument.application.service;

import com.kjw.tradepilot.instrument.application.port.in.InstrumentSearchQuery;
import com.kjw.tradepilot.instrument.application.port.out.InstrumentRepositoryPort;
import com.kjw.tradepilot.instrument.domain.SecurityInstrument;
import com.kjw.tradepilot.marketdata.domain.Market;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Locale;
import java.util.Objects;

@Service
public class InstrumentSearchService implements InstrumentSearchQuery {
    private static final int MAX_QUERY_LENGTH = 50;
    private static final int MAX_RESULT_SIZE = 20;

    private final InstrumentRepositoryPort repository;

    public InstrumentSearchService(InstrumentRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Flux<SecurityInstrument> search(Market market, String query, int limit) {
        Objects.requireNonNull(market, "market must not be null");
        String normalizedQuery = normalizeQuery(query);
        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_RESULT_SIZE);
        return repository.search(market, normalizedQuery, normalizedLimit);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        String normalized = query.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query must not exceed " + MAX_QUERY_LENGTH + " characters");
        }
        return normalized;
    }
}
