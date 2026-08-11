package com.kjw.tradepilot.marketdata.adapter.out.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjw.tradepilot.marketdata.application.port.out.LatestQuoteCachePort;
import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
class RedisLatestQuoteCacheAdapter implements LatestQuoteCachePort {
    private static final String KEY_PREFIX = "market-data:latest:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    RedisLatestQuoteCacheAdapter(
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${tradepilot.market-data.latest-quote-ttl:24h}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Mono<Void> put(MarketTick tick) {
        return serialize(tick)
                .flatMap(json -> redisTemplate.opsForValue().set(key(tick.market(), tick.symbol()), json, ttl))
                .then();
    }

    @Override
    public Mono<MarketTick> get(Market market, String symbol) {
        return redisTemplate.opsForValue().get(key(market, symbol)).flatMap(this::deserialize);
    }

    private Mono<String> serialize(MarketTick tick) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(tick));
    }

    private Mono<MarketTick> deserialize(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, MarketTick.class));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    private String key(Market market, String symbol) {
        return KEY_PREFIX + market.name() + ":" + symbol;
    }
}
