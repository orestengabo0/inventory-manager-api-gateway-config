package rca.restapi.year2.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    private static Logger logger = Logger.getLogger(LoggingFilter.class.getName());

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        logger.info("Incoming Request: " +
                request.getMethod() + " " +
                request.getPath() + " from " +
                request.getHeaders());

        logger.info("Headers: "+ request.getHeaders());

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            logger.info("Response status: "+ exchange.getResponse().getStatusCode());
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
