package com.kjw.tradepilot.portfolio.adapter.in.web;

import com.kjw.tradepilot.portfolio.application.port.in.PortfolioQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/portfolio")
class PortfolioQueryController {
    private final PortfolioQuery portfolioQuery;

    PortfolioQueryController(PortfolioQuery portfolioQuery) {
        this.portfolioQuery = portfolioQuery;
    }

    @GetMapping("/accounts/{accountId}/summary")
    Mono<ResponseEntity<PortfolioSummaryResponse>> getSummary(@PathVariable String accountId) {
        return portfolioQuery.getSnapshot(accountId)
                .map(PortfolioSummaryResponse::from)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
