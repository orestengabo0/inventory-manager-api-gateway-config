package rca.restapi.year2.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.AuthenticationFilter;

@Configuration
public class GatewayConfig {
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder){
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Service", "auth-service")
                        )
                        .uri("lb://auth-service")
                )

                // Catalog Service Routes
                .route("catalog-service-public", r -> r
                        .path("/api/products/**")
                        .and()
                        .method("GET")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Service", "catalog-service")
                        )
                        .uri("lb://catalog-service"))

                .route("catalog-service-admin", r -> r
                        .path("/api/products/**", "/api/admin/products/**")
                        .and()
                        .method("POST", "PUT", "DELETE")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Service", "catalog-service")
                                .filter(authenticationFilter())
                        )
                        .uri("lb://catalog-service"))

                // Cart Service Routes
                .route("cart-service", r -> r
                        .path("/api/cart/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Service", "cart-service")
                                .filter(authenticationFilter())
                        )
                        .uri("lb://cart-service"))

                // Order Service Routes
                .route("order-service", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Service", "order-service")
                                .filter(authenticationFilter())
                        )
                        .uri("lb://order-service"))

                // Discount Service Routes
                .route("discount-service", r -> r
                        .path("/api/discounts/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Service", "discount-service")
                        )
                        .uri("lb://discount-service"))

                .build();
    }

    @Bean
    public AuthenticationFilter authenticationFilter(){
        return new AuthenticationFilter();
    }
}
