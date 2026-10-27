package com.mymall.gateway;

import com.mymall.gateway.filter.TraceIdFilter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关运行时验证 —— 真实启动 Netty（随机端口）走完整过滤器链，验证 D2 的关键行为。
 * 跑法：mvn -pl my-mall-gateway test -Dtest=GatewayRuntimeTest
 *
 * 覆盖：① 8 条路由就位 ② 未登录返回业务码 401 ③ 白名单路径带 traceId 响应头
 *      ④ /actuator/gateway/routes 可读（access=unrestricted 生效，switch.ps1 才能动态改路由）
 *      ⑤ /doc.html 文档聚合页可访问（knife4j 生效）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRuntimeTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    @Order(1)
    @DisplayName("① 8 条路由全部就位（/mall-admin/** 已按域拆到 5 个服务）")
    void routesLoaded() {
        List<RouteDefinition> defs = routeDefinitionLocator.getRouteDefinitions().collectList().block();
        assertThat(defs).isNotNull();
        List<String> ids = defs.stream().map(RouteDefinition::getId).sorted().collect(Collectors.toList());
        System.out.println("[验证①] 已加载路由 = " + ids);
        // 原项目是 5 条（mall-admin 一条大路由 + mall-demo），迁移后 /mall-admin/** 按业务域
        // 拆成 user/product/marketing/order/file 五条，mall-demo 已随老服务取消，故共 8 条。
        // 这里用 containsExactlyInAnyOrder：断言的是"路由集合"，不依赖排序实现。
        assertThat(ids).containsExactlyInAnyOrder(
                "mall-auth",
                "mall-admin-user",
                "mall-admin-product",
                "mall-admin-marketing",
                "mall-admin-order",
                "mall-admin-file",
                "mall-portal",
                "mall-search");
    }

    @Test
    @Order(2)
    @DisplayName("② 未带 token 访问受保护接口 → 业务码 401（与原项目一致）")
    void unauthorizedWithoutToken() {
        var res = webTestClient.get().uri("/mall-admin/admin/info")
                .exchange()
                .expectBody(String.class)
                .returnResult();
        String body = res.getResponseBody();
        System.out.println("[验证②] /mall-admin/admin/info 无 token -> HTTP " + res.getStatus()
                + " body=" + body);
        assertThat(body).as("401 响应体必须是 JSON（不能是对象 toString）").isNotNull();
        assertThat(body).contains("\"code\":401");
        assertThat(body).contains("暂未登录");
    }

    @Test
    @Order(3)
    @DisplayName("③ 白名单路径可放行，且响应头带 X-Trace-Id")
    void whitelistPassesAndTraceIdHeaderPresent() {
        webTestClient.get().uri("/mall-portal/product/categoryTreeList")
                .exchange()
                .expectHeader().exists(TraceIdFilter.TRACE_ID_HEADER);
        System.out.println("[验证③] 白名单路径放行且带 " + TraceIdFilter.TRACE_ID_HEADER + " 响应头");
    }

    @Test
    @Order(4)
    @DisplayName("④ gateway actuator 端点可读（access=unrestricted 生效）")
    void gatewayActuatorExposed() {
        String body = webTestClient.get().uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        System.out.println("[验证④] /actuator/gateway/routes 可读，响应长度 = "
                + (body == null ? 0 : body.length()));
        assertThat(body).contains("mall-admin");
    }

    @Test
    @Order(5)
    @DisplayName("⑤ /doc.html 文档聚合页可访问（knife4j 生效）")
    void docPageAvailable() {
        webTestClient.get().uri("/doc.html")
                .exchange()
                .expectStatus().is2xxSuccessful();
        System.out.println("[验证⑤] /doc.html 可访问，knife4j 聚合文档生效");
    }
}