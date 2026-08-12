package com.kjw.tradepilot.watchlist.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.watchlist.application.port.in.WatchlistUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/watchlist")
class WatchlistController {
    private final WatchlistUseCase watchlistUseCase;

    WatchlistController(WatchlistUseCase watchlistUseCase) {
        this.watchlistUseCase = watchlistUseCase;
    }

    @GetMapping
    Flux<WatchlistItemResponse> getWatchlist(@PathVariable String accountId) {
        return watchlistUseCase.getWatchlist(accountId).map(WatchlistItemResponse::from);
    }

    @PostMapping("/items")
    Mono<ResponseEntity<WatchlistItemResponse>> addItem(
            @PathVariable String accountId,
            @Valid @RequestBody AddWatchlistItemRequest request
    ) {
        return watchlistUseCase.addItem(accountId, request.symbol(), request.market(), request.name())
                .map(WatchlistItemResponse::from)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/v1/accounts/" + accountId + "/watchlist/items/" + response.symbol()))
                        .body(response));
    }

    @DeleteMapping("/items/{symbol}")
    Mono<ResponseEntity<Void>> removeItem(
            @PathVariable String accountId,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "KRX") Market market
    ) {
        return watchlistUseCase.removeItem(accountId, market, symbol)
                .map(removed -> removed
                        ? ResponseEntity.noContent().build()
                        : ResponseEntity.notFound().build());
    }

    @PatchMapping("/order")
    Flux<WatchlistItemResponse> reorder(
            @PathVariable String accountId,
            @Valid @RequestBody ReorderWatchlistRequest request
    ) {
        return watchlistUseCase.reorder(accountId, request.toKeys()).map(WatchlistItemResponse::from);
    }
}
