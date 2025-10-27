package rca.restapi.year2.apigateway.filter;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class TokenBucketRateLimiter {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private final RedisScript<String> tokenBucketScript;

    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_token = tonumber(ARGV[2])
            local refill_interval = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])
            local requested_tokens = tonumber(ARGV[5])
            
            -- Get current bucke state
            local bucket_data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local current_tokens = capacity
            local last_refill = now
                
            if bucket_data[1] then
                current_tokens = tonumber(bucket_data[1])
                last_refill = tonumber(bucket_data[2])
            end
            
            -- Calculate time passed and refill tokens
            local time_passed = now - last_refill
            local refill_intervals = math.floor(time_passed / refill_interval)
            
            if refill_intervals > 0 then
                current_tokens = math.min(capacity, current_tokens + (refill_intervals * refill_tokens))
                last_refill = last_refill + (refill_intervals * refill_interval)
            end
            
            -- Check if we can consume the requested tokens
            local allowed = false
            local remaining_tokens = current_tokens
            local retry_after = 0
            
            if current_tokens >= requested_tokens then
                    current_tokens = current_tokens - requested_tokens
                        remaining_tokens = current_tokens
                        allowed = true
                        -- Update bucket state
                        redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill', last_refill)
                        -- Set expiration (clean up after 2x the time it would take to fill empty bucket)
                        local ttl = math.ceil(capacity / refill_tokens) * refill_interval * 2
                        redis.call('EXPIRE', key, ttl)
                    else
                        -- Calculate when enough tokens will be available
                        local tokens_needed = requested_tokens - current_tokens
                        local intervals_needed = math.ceil(tokens_needed / refill_tokens)
                        retry_after = (intervals_needed * refill_interval) - (now - last_refill)
                        retry_after = math.max(1, retry_after) -- At least 1 second
            end
            
            return tostring(allowed) .. '|' .. tostring(math.floor(remaining_tokens)) .. '|' .. tostring(retry_after)
            """;

    public TokenBucketRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate){
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = RedisScript.of(TOKEN_BUCKET_SCRIPT, String.class);
    }

    public Mono<RateLimitResult> tryConsume(String clientKey, int capacity, int refillTokens,
                                            int refillIntervalSeconds, int requestedTokens){
        long currentTimeSeconds = Instant.now().getEpochSecond();

        return redisTemplate.execute(tokenBucketScript,
                java.util.List.of(buildRedisKey(clientKey)),
                String.valueOf(capacity),
                String.valueOf(refillTokens),
                String.valueOf(refillIntervalSeconds),
                String.valueOf(currentTimeSeconds),
                String.valueOf(requestedTokens))
                .next()
                .map(this::parseResult)
                .defaultIfEmpty(new RateLimitResult(true, capacity - requestedTokens, 0));
    }

    private String buildRedisKey(String clientKey){
        return "rate_limit:token_bucket:" +clientKey;
    }

    private RateLimitResult parseResult(Object result) {
        if (result == null) {
            return new RateLimitResult(true, 0, 0);
        }

        String[] parts = result.toString().split("\\|");
        if (parts.length < 3) {
            return new RateLimitResult(true, 0, 0);
        }

        boolean allowed = Boolean.parseBoolean(parts[0]);
        int remainingTokens = Integer.parseInt(parts[1]);
        long retryAfterSeconds = Long.parseLong(parts[2]);

        return new RateLimitResult(allowed, remainingTokens, retryAfterSeconds);
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final int remainingTokens;
        private final long retryAfterSeconds;

        public RateLimitResult(boolean allowed, int remainingTokens, long retryAfterSeconds) {
            this.allowed = allowed;
            this.remainingTokens = remainingTokens;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        // Getters
        public boolean isAllowed() { return allowed; }
        public int getRemainingTokens() { return remainingTokens; }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}
