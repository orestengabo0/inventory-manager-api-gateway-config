package rca.restapi.year2.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Public endpoints
                        .pathMatchers(
                                "/api/auth/**",
                                "/api/products/**",
                                "/api/discounts/calculate",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Protected endpoints
                        .pathMatchers("/api/cart/**", "/api/orders/**").hasAnyRole("USER", "ADMIN")
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")

                        // All other endpoints require authentication
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwkSetUri("http://auth-service/oauth2/jwks"))
                )
                .build();
    }
}
