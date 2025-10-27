package rca.restapi.year2.apigateway.filter;

import lombok.Data;
import org.apache.http.HttpStatus;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimitingFilter extends AbstractGatewayFilterFactory<RateLimitingFilter.Config> {
    private final TokenBucketRateLimiter rateLimiter;

    public RateLimitingFilter(TokenBucketRateLimiter rateLimiter) {
        super(Config.class);
        this.rateLimiter = rateLimiter;
    }

    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String clientKey = getClientIdentifier(exchange, config);
            return rateLimiter.tryConsume(clientKey, config.getCapacity(), config.getRefillTokens(), config.getRefillIntervalSeconds(), 1)
                    .flatMap(rateLimitResult -> {
                        if(rateLimitResult.isAllowed()) {
                            addRateLimitHeaders(exchange, rateLimitResult, config);
                            return chain.filter(exchange);
                        }else {
                            return handleRateLimitExceeded(exchange, rateLimitResult);
                        }
                    })
                    .onErrorResume(throwable -> {
                        System.err.println("Rate limiting failed: " + throwable.getMessage());
                        return chain.filter(exchange);
                    });
        };
    }

    private String getClientIdentifier(ServerWebExchange exchange, Config config) {
        return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    private void addRateLimitHeaders(ServerWebExchange exchange, TokenBucketRateLimiter.RateLimitResult rateLimitResult, Config config) {
        var headers = exchange.getResponse().getHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(config.getCapacity()));
        headers.add("X-RateLimit-Remaining", String.valueOf(rateLimitResult.getRemainingTokens()));
        headers.add("X-RateLimit-Policy", config.getName());
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange, TokenBucketRateLimiter.RateLimitResult rateLimitResult) {
        exchange.getResponse().setRawStatusCode(HttpStatus.SC_TOO_MANY_REQUESTS);
        var headers = exchange.getResponse().getHeaders();
        headers.add("X-RateLimit-Limit", "0");
        headers.add("X-RateLimit-Remaining", "0");
        headers.add("Retry-After", String.valueOf(rateLimitResult.getRetryAfterSeconds()));
        return exchange.getResponse().setComplete();
    }

    @Data
    public static class Config {
        private int capacity = 10;
        private int refillTokens = 5;
        private int refillIntervalSeconds = 60;
        private String name = "default";

        public Config() {}

        public Config(int capacity, int refillTokens, int refillIntervalSeconds, String name) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillIntervalSeconds = refillIntervalSeconds;
            this.name = name;
        }
    }
}
