package com.mymall.gateway.filter;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * traceId 过滤器（原项目没有，属于净增益）。
 *
 * 为什么用 WebFilter 而不是 GlobalFilter：GlobalFilter 只在"路由匹配成功"后才执行，
 * 404 / 未匹配路由的请求就拿不到 traceId —— 而这类请求恰恰最需要 traceId 排障。
 *
 * 传递方式：写回请求头（下游服务读取后写进自己的日志）→ 全链路可串；
 *          同时写响应头，方便用户/脚本报出 traceId。
 *          后续接入skywalking，把生成x-Trace-id改成在skywalking获取
 *          response.getHeaders().set("X-Trace-Id", TraceContext.traceId());
 *          后期替换为 skywalking，淘汰tracefilter
 */
@Component
public class TraceIdFilter implements WebFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        final String tid = traceId;

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, tid)
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getResponse().getHeaders().set(TRACE_ID_HEADER, tid);

        MDC.put("traceId", tid);
        return chain.filter(mutated)
                .doFinally(signal -> MDC.remove("traceId"));
    }

    @Override
    public int getOrder() {
        // 最先执行：所有请求（包括 404）都要有 traceId
        return Ordered.HIGHEST_PRECEDENCE;
    }
}