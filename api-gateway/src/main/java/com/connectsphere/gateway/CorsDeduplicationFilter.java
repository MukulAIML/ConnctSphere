package com.connectsphere.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
public class CorsDeduplicationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            List<String> origins = headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
            if (origins != null && origins.size() > 1) {
                // Keep only the first or last one
                String uniqueOrigin = origins.get(origins.size() - 1);
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, uniqueOrigin);
            }
            List<String> credentials = headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
            if (credentials != null && credentials.size() > 1) {
                String uniqueCred = credentials.get(credentials.size() - 1);
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, uniqueCred);
            }
        }));
    }

    @Override
    public int getOrder() {
        // Run AFTER NettyWriteResponseFilter (which is Ordered.LOWEST_PRECEDENCE - 1)
        return Ordered.LOWEST_PRECEDENCE;
    }
}
