package com.mymall.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * traceId 过滤器（Servlet 栈），与网关的 com.mymall.gateway.filter.TraceIdFilter 构成一对：
 *   网关：生成/透传请求头 X-Trace-Id，并写响应头；
 *   本类：把上游传来的 traceId 放进本进程 MDC（日志串联），直连服务时也能自造一个，并回写响应头。
 *
 * 常量必须与网关逐字一致：头名 X-Trace-Id、MDC key traceId。
 * 注意：要让 traceId 真正打印到日志里，服务还需在 logging.pattern.console 中加 %X{traceId}。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        // Tomcat 线程会复用，这里记录旧值并在 finally 还原，避免串到下个请求
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}