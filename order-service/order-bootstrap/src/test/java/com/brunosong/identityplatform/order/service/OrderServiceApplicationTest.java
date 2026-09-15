package com.brunosong.identityplatform.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 서비스가 실제로 뜨는지, 그리고 <b>토큰 없이는 아무것도 열리지 않는지</b> 본다.
 *
 * <p>auth-service 를 띄우지 않고 돈다. 발급자 주소는 닿지 않는 곳을 가리키는데, 그래도 부팅은 성공해야
 * 한다. 소비 서비스가 auth 보다 먼저 뜨는 일은 흔하고, 그때 부팅이 깨지면 기동 순서가 강제된다.
 * {@code issuer-uri} 를 쓰면 Spring 이 디코더 생성을 첫 검증까지 미루므로({@code SupplierJwtDecoder})
 * 부팅 시점에 auth 를 부르지 않는다.
 *
 * <p>키를 못 받으면 검증은 실패하고, 실패한 검증은 401 이다. auth 가 없는 동안 이 서비스는
 * "아무도 로그인하지 않은 상태" 로 동작한다. 열린 채로 남지 않는다는 것이 중요하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class OrderServiceApplicationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // 닿지 않는 주소다. auth 없이도 떠야 하고, 토큰 없는 요청은 어차피 401 이다.
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:1/realms/portal");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("auth-service 가 없어도 컨텍스트가 뜬다")
    void contextLoadsWithoutAuthService() {
        assertThat(context.getBean(JwtDecoder.class)).isNotNull();
        assertThat(context.getBean(SecurityFilterChain.class)).isNotNull();
    }

    @Test
    @DisplayName("토큰 없이는 401, 검증할 수 없는 토큰도 401")
    void withoutValidTokenEverythingIsUnauthorized() {
        RestClient http = client();

        assertThat(http.get().uri("/api/orders").retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(401);

        // 형식만 갖춘 가짜 토큰. 공개키를 받아올 수 없으므로 서명 검증에서 걸린다.
        assertThat(http.get().uri("/api/orders/anything")
                .header("Authorization", "Bearer eyJraWQiOiJ4IiwiYWxnIjoiUlMyNTYifQ.e30.x")
                .retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(401);

        // 주문도 마찬가지다. 인증 없이 남의 이름으로 주문할 수 없다.
        assertThat(http.post().uri("/api/orders")
                .header("Content-Type", "application/json")
                .body(Map.of("productName", "침입자", "quantity", 1, "unitPrice", 1000))
                .retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("인증 실패는 표준대로 알린다: WWW-Authenticate: Bearer")
    void unauthorizedCarriesTheStandardChallenge() {
        assertThat(client().get().uri("/api/orders").retrieve().toBodilessEntity()
                .getHeaders().getFirst("WWW-Authenticate"))
                .isEqualTo("Bearer");
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })   // 상태코드를 직접 본다
                .build();
    }
}
