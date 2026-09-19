package com.mymall.gateway.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mymall.common.api.CommonResult;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一 JSON 响应写出。
 * WebFlux 里必须 response.writeWith(Mono.just(buffer))，只调 setComplete() 会返回空 body。
 * 头部与原项目 handleException 保持一致（JSON + CORS + no-cache）。
 */
public final class GatewayResponseWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GatewayResponseWriter() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, Object body) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Access-Control-Allow-Origin", "*");
        response.getHeaders().setCacheControl("no-cache");
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"code\":500,\"message\":\"serialize error\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    public static Mono<Void> unauthorized(ServerWebExchange exchange) {
        return write(exchange, CommonResult.unauthorized(null));
    }

    public static Mono<Void> forbidden(ServerWebExchange exchange) {
        return write(exchange, CommonResult.forbidden(null));
    }

    public static Mono<Void> failed(ServerWebExchange exchange, String message) {
        return write(exchange, CommonResult.failed(message));
    }

    public static Mono<Void> serviceUnavailable(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return write(exchange, CommonResult.failed(message));
    }
}