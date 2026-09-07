package com.brunosong.identityplatform.auth.service;

import com.brunosong.identityplatform.auth.client.AuthTokenVerifier;
import com.brunosong.identityplatform.auth.client.AuthenticatedToken;
import com.brunosong.identityplatform.auth.client.JwksKeySource;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>서비스 간 신뢰가 실제로 서는지</b> 본다 — auth 가 토큰을 발급하고, 다른 서비스가 auth 에 묻지 않고
 * 그 토큰을 검증하는 전 구간이다. 여기서 auth-client 는 "남의 서비스" 역할이다.
 *
 * <p>이 테스트가 없으면 발급기와 검증기가 조용히 갈라진다. 클레임 이름 하나만 바뀌어도 auth 쪽 테스트는
 * 전부 통과하고 소비 서비스에서만 깨진다.
 *
 * <p>실제 HTTP 로 돈다(랜덤 포트). JWKS 조회는 네트워크 경계를 넘는 일이라 그 경계까지 포함해야
 * 확인이 된다 — 직렬화, base64url 인코딩, 응답 모양이 여기서만 드러난다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class JwksVerificationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RegisterWithPasswordUseCase registerWithPassword;

    private RestClient http;
    /** 소비 서비스가 갖게 될 검증기. auth 의 내부 빈이 아니라 JWKS 주소만 알고 있다. */
    private AuthTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
        verifier = new AuthTokenVerifier(new JwksKeySource(
                RestClient.create(),
                "http://localhost:" + port + "/.well-known/jwks.json",
                Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("JWKS 는 두 realm 의 공개키를 인증 없이 내준다")
    void jwksExposesBothRealmKeys() {
        Map<?, ?> document = http.get().uri("/.well-known/jwks.json").retrieve().body(Map.class);

        List<?> keys = (List<?>) document.get("keys");
        assertThat(keys).hasSize(2);
        assertThat(keys).allSatisfy(key -> {
            Map<String, Object> jwk = asMap(key);
            assertThat(jwk).containsEntry("kty", "RSA").containsEntry("use", "sig")
                    .containsEntry("alg", "RS256");
            // n/e 가 없으면 소비 서비스가 키를 만들 수 없다. 있는지까지 본다.
            assertThat(jwk.get("n")).asString().isNotBlank();
            assertThat(jwk.get("e")).asString().isNotBlank();
        });

        // kid 는 realm 마다 달라야 한다 — 겹치면 상대 realm 키로 검증이 통과한다.
        assertThat(keys.stream().map(k -> asMap(k).get("kid")))
                .containsExactlyInAnyOrder("employee-local", "customer-local");
    }

    @Test
    @DisplayName("발급된 토큰을 다른 서비스가 auth 에 묻지 않고 검증한다")
    void anotherServiceVerifiesIssuedToken() {
        String email = "verify-" + UUID.randomUUID() + "@example.com";
        registerCustomer(email);

        String accessToken = login(email);

        AuthenticatedToken token = verifier.verify(accessToken).orElseThrow();
        assertThat(token.realm()).isEqualTo("CUSTOMER");
        assertThat(token.email()).isEqualTo(email);
        assertThat(token.subjectId()).isNotBlank();
    }

    @Test
    @DisplayName("서명이 맞아도 realm 이 다르면 통과시키지 않는다")
    void signatureAloneIsNotEnough() {
        String email = "realm-" + UUID.randomUUID() + "@example.com";
        registerCustomer(email);
        String accessToken = login(email);

        // 두 realm 의 공개키가 같은 JWKS 에 함께 있으므로 서명만으로는 realm 이 가려지지 않는다.
        // 직원 전용 서비스가 이 확인을 빠뜨리면 고객 토큰으로 열린다.
        assertThat(verifier.verify(accessToken, "CUSTOMER")).isPresent();
        assertThat(verifier.verify(accessToken, "EMPLOYEE")).isEmpty();
    }

    @Test
    @DisplayName("refresh 토큰은 access 토큰으로 쓰이지 않는다")
    void refreshTokenIsNotAccepted() {
        String email = "refresh-" + UUID.randomUUID() + "@example.com";
        registerCustomer(email);

        Map<?, ?> tokens = (Map<?, ?>) loginResponse(email).get("tokens");
        String refreshToken = (String) tokens.get("refreshToken");

        // 같은 키로 서명돼 있어 서명 검증은 통과한다. type 클레임이 그 둘을 가른다.
        assertThat(verifier.verify(refreshToken)).isEmpty();
    }

    @Test
    @DisplayName("망가진 토큰과 빈 값은 예외가 아니라 빈 결과다")
    void invalidTokensAreEmptyNotErrors() {
        assertThat(verifier.verify(null)).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
        assertThat(verifier.verify("not-a-jwt")).isEmpty();
        // 모르는 kid — JWKS 를 다시 받아봐도 없으면 그냥 실패다(예외를 던지지 않는다).
        assertThat(verifier.verify("eyJraWQiOiJ1bmtub3duIiwiYWxnIjoiUlMyNTYifQ.e30.x")).isEmpty();
    }

    @Test
    @DisplayName("Authorization 헤더 값에서 바로 검증한다")
    void verifiesFromAuthorizationHeader() {
        String email = "header-" + UUID.randomUUID() + "@example.com";
        registerCustomer(email);
        String accessToken = login(email);

        assertThat(verifier.verifyAuthorizationHeader("Bearer " + accessToken)).isPresent();
        assertThat(verifier.verifyAuthorizationHeader(accessToken)).isEmpty();
        assertThat(verifier.verifyAuthorizationHeader(null)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    /** 가입은 유스케이스로 직접 부른다 — 이 테스트가 보려는 것은 가입 API 가 아니라 토큰 검증이다. */
    private void registerCustomer(String email) {
        registerWithPassword.register(new RegisterWithPasswordCommand(
                SubjectType.CUSTOMER, email, "Tester", null, email, "pw12345678"));
    }

    private String login(String email) {
        Map<?, ?> tokens = (Map<?, ?>) loginResponse(email).get("tokens");
        return (String) tokens.get("accessToken");
    }

    private Map<?, ?> loginResponse(String email) {
        return Optional.ofNullable(http.post()
                        .uri("/api/auth/realms/customer/login")
                        .header("Content-Type", "application/json")
                        .body(Map.of("loginId", email, "password", "pw12345678"))
                        .retrieve()
                        .body(Map.class))
                .orElseThrow(() -> new IllegalStateException("로그인 응답이 비어 있다"));
    }
}
