package com.kjw.tradepilot.portfolio.application.service;

import com.kjw.tradepilot.marketdata.application.port.in.MarketDataQuery;
import com.kjw.tradepilot.portfolio.application.port.in.PortfolioQuery;
import com.kjw.tradepilot.portfolio.application.port.out.PortfolioRepositoryPort;
import com.kjw.tradepilot.portfolio.domain.PortfolioPosition;
import com.kjw.tradepilot.portfolio.domain.PortfolioSnapshot;
import com.kjw.tradepilot.portfolio.domain.PositionValuation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;

@Service
public class PortfolioService implements PortfolioQuery {
    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final PortfolioRepositoryPort portfolioRepository;
    private final MarketDataQuery marketDataQuery;

    public PortfolioService(PortfolioRepositoryPort portfolioRepository, MarketDataQuery marketDataQuery) {
        this.portfolioRepository = portfolioRepository;
        this.marketDataQuery = marketDataQuery;
    }

    @Override
    public Mono<PortfolioSnapshot> getSnapshot(String accountId) {
        String normalizedAccountId = normalizeAccountId(accountId);

        return portfolioRepository.findAccount(normalizedAccountId)
                .flatMap(account -> portfolioRepository.findPositions(normalizedAccountId)
                        .flatMap(this::evaluatePosition, 8)
                        .sort(Comparator.comparing(PositionValuation::symbol))
                        .collectList()
                        .map(positions -> PortfolioSnapshot.from(account, positions)));
    }

    private Mono<PositionValuation> evaluatePosition(PortfolioPosition position) {
        return marketDataQuery.getLatest(position.market(), position.symbol())
                .map(tick -> PositionValuation.quoted(position, tick.price(), tick.tradedAt()))
                .onErrorResume(error -> {
                    log.warn("Portfolio quote lookup failed: market={}, symbol={}",
                            position.market(), position.symbol(), error);
                    return Mono.empty();
                })
                .defaultIfEmpty(PositionValuation.unavailable(position));
    }

    private String normalizeAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        return accountId.trim();
    }
}
