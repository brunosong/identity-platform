package com.brunosong.identityplatform.order.service;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 서비스가 <b>새로 주장하는 것</b>을 확인한다. customer-service 에는 이 자리가 없었다.
 *
 * <ol>
 *   <li>남의 주문번호는 403 이 아니라 <b>404</b> 다. 403 이면 "그 주문은 존재한다" 가 새어나간다</li>
 *   <li>읽기 권한만으로는 주문하지 못한다. 역할이 둘로 갈려 있다</li>
 *   <li>같은 토큰에 실린 <b>다른 서비스의 역할</b>은 여기서 아무것도 열지 못한다</li>
 * </ol>
 *
 * <p>토큰의 {@code aud} 는 {@code shop} 하나다. customer-service 도 같은 값을 요구하므로 한
 * 토큰이 둘 다에 통한다. 서비스를 가르는 것은 {@code aud} 가 아니라 {@code resource_access} 의
 * 칸이고, 아래 마지막 테스트가 그것을 확인한다.
 *
 * <p>토큰을 여기서 직접 만든다. auth-service 를 띄우지 않고 검증 경로만 보려는 것이고, 실제 발급
 * 로직은 auth 쪽 테스트가 본다. auth 의 모듈을 의존하지 않고 표준 라이브러리로만 서명하는 것도
 * 그대로다. 이 서비스가 auth 에 대해 아는 것은 클레임 모양뿐이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class MyOrderApiSecurityTest {

    private static final String ISSUER = "http://localhost:8080/realms/portal";
    /** aud 는 시스템이다. customer-service 도 같은 값을 요구한다. */
    private static final String SYSTEM = "shop";
    /** resource_access 의 칸 이름. aud 와 다른 값이고, 이것이 서비스를 가른다. */
    private static final String SERVICE_ID = "order-service";

    private static final KeyPair KEYS = generateKeys();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> SYSTEM);
        registry.add("app.service-id", () -> SERVICE_ID);
    }

    /**
     * JWKS 를 받아오는 대신 공개키를 직접 쥐여준다. 바뀌는 것은 <b>키를 어디서 얻느냐</b> 하나뿐이고,
     * 서명과 만료 검증은 운영과 같은 경로를 탄다.
     */
    @TestConfiguration
    static class LocalKeyDecoder {
        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        }
    }

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("쓰기 권한으로 주문하고 읽기 권한으로 조회한다")
    void placeAndRead() {
        RestClient http = client();
        String token = tokenFor("cust-1", Map.of(SERVICE_ID, roles("ORDER_READ", "ORDER_WRITE")));

        Map<?, ?> placed = http.post().uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .body(Map.of("productName", "키보드", "quantity", 2, "unitPrice", 45000))
                .retrieve().body(Map.class);

        // 금액은 단가 곱하기 수량이다. 본문으로 받지 않는다. 받으면 낼 값을 스스로 정할 수 있다.
        assertThat(placed.get("quantity")).isEqualTo(2);
        assertThat(new BigDecimal(String.valueOf(placed.get("amount")))).isEqualByComparingTo("90000");

        String orderId = String.valueOf(placed.get("orderId"));
        assertThat(http.get().uri("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("남의 주문번호는 404 다. 403 이면 그 주문이 있다는 사실이 새어나간다")
    void someoneElseOrderIsNotFound() {
        RestClient http = client();
        String mine = tokenFor("cust-owner", Map.of(SERVICE_ID, roles("ORDER_READ", "ORDER_WRITE")));
        String theirs = tokenFor("cust-other", Map.of(SERVICE_ID, roles("ORDER_READ")));

        Map<?, ?> placed = http.post().uri("/api/orders")
                .header("Authorization", "Bearer " + mine)
                .header("Content-Type", "application/json")
                .body(Map.of("productName", "마우스", "quantity", 1, "unitPrice", 20000))
                .retrieve().body(Map.class);
        String orderId = String.valueOf(placed.get("orderId"));

        // 읽기 권한은 멀쩡히 있다. 막는 것은 역할이 아니라 소유권이고, 그 답이 404 다.
        assertThat(http.get().uri("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + theirs)
                .retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(404);

        // 없는 주문번호와 구분되지 않는다. 구분되면 번호를 훑어 남의 주문을 셀 수 있다.
        assertThat(http.get().uri("/api/orders/does-not-exist")
                .header("Authorization", "Bearer " + theirs)
                .retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(404);

        // 목록에도 남의 주문은 오지 않는다.
        assertThat(http.get().uri("/api/orders")
                .header("Authorization", "Bearer " + theirs)
                .retrieve().body(List.class))
                .isEmpty();
    }

    @Test
    @DisplayName("읽기 권한만으로는 주문하지 못한다")
    void readRoleCannotWrite() {
        assertThat(client().post().uri("/api/orders")
                .header("Authorization", "Bearer "
                        + tokenFor("cust-1", Map.of(SERVICE_ID, roles("ORDER_READ"))))
                .header("Content-Type", "application/json")
                .body(Map.of("productName", "키보드", "quantity", 1, "unitPrice", 1000))
                .retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("다른 서비스 칸의 역할은 여기서 아무것도 열지 못한다")
    void rolesOfAnotherServiceOpenNothingHere() {
        // 같은 토큰에 customer-service 의 칸이 함께 실려 있다. 통합 로그인이라 정상이다.
        // 그 칸에 ORDER_READ 와 같은 이름이 들어 있어도 이 서비스는 자기 칸만 읽는다.
        String token = tokenFor("cust-1", Map.of(
                "customer-service", roles("PROFILE_READ", "ORDER_READ", "ORDER_WRITE")));

        assertThat(client().get().uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(403);
    }

    // ---------------------------------------------------------------------

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();
    }

    private static Map<String, Object> roles(String... codes) {
        return Map.of("roles", List.of(codes));
    }

    /** auth 가 내보내는 access 토큰과 같은 모양으로 만든다. */
    private static String tokenFor(String subject, Map<String, Object> resourceAccess) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of(SYSTEM))
                .subject(subject)
                .claim("type", "access")
                .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                .claim("resource_access", resourceAccess)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT).keyID("portal-test").build(),
                    claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
