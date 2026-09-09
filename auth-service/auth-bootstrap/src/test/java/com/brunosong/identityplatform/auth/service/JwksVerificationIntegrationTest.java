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
 *
 * <p>여기서 만드는 검증기는 <b>포털 realm 만</b> 상대한다. 실제 소비 서비스(customer-service)가
 * 그렇게 설정돼 있기 때문이고, 그래서 이 테스트가 "한 realm 만 보는 서비스" 의 표본이 된다.
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
                "http://localhost:" + port + "/realms/portal/.well-known/jwks.json",
                Duration.ofMinutes(10)), "PORTAL");
    }

    @Test
    @DisplayName("JWKS 는 realm 마다 그 realm 의 키만 인증 없이 내준다")
    void jwksIsPerRealm() {
        assertThat(kidsOf("portal")).containsExactly("portal-local");
        assertThat(kidsOf("admin")).containsExactly("admin-local");
    }

    @Test
    @DisplayName("합쳐진 JWKS 는 없다")
    void thereIsNoCombinedJwks() {
        // 한 문서에 모든 realm 의 키가 있으면 어느 서비스든 모든 realm 의 토큰을 검증할 수 있게 되고,
        // realm 경계는 소비 서비스가 클레임을 확인해 주기를 바라는 것으로만 남는다.
        assertThat(statusOf("/.well-known/jwks.json")).isEqualTo(404);
    }

    @Test
    @DisplayName("모르는 realm 의 JWKS 는 404 다")
    void unknownRealmJwksIsNotFound() {
        assertThat(statusOf("/realms/martian/.well-known/jwks.json")).isEqualTo(404);
    }

    @Test
    @DisplayName("JWK 는 공개키를 되살릴 수 있는 값을 담는다")
    void jwkCarriesEnoughToRebuildTheKey() {
        Map<?, ?> document = http.get()
                .uri("/realms/portal/.well-known/jwks.json").retrieve().body(Map.class);

        Map<String, Object> jwk = asMap(((List<?>) document.get("keys")).get(0));
        assertThat(jwk).containsEntry("kty", "RSA").containsEntry("use", "sig")
                .containsEntry("alg", "RS256");
        // RSA 공개키는 모듈러스(n)와 지수(e) 두 값이면 복원된다. 없으면 소비 서비스가 키를 못 만든다.
        assertThat(jwk.get("n")).asString().isNotBlank();
        assertThat(jwk.get("e")).asString().isNotBlank();
        // 개인키를 이루는 값은 없어야 한다 — 그래서 이 문서를 아무에게나 줘도 된다.
        assertThat(jwk).doesNotContainKeys("d", "p", "q");
    }

    @Test
    @DisplayName("발급된 토큰을 다른 서비스가 auth 에 묻지 않고 검증한다")
    void anotherServiceVerifiesIssuedToken() {
        String email = "verify-" + UUID.randomUUID() + "@example.com";
        registerCustomer(email);

        String accessToken = login(email);

        AuthenticatedToken token = verifier.verify(accessToken).orElseThrow();
        assertThat(token.realm()).isEqualTo("PORTAL");
        assertThat(token.subjectId()).isNotBlank();
    }

    @Test
    @DisplayName("포털만 보는 검증기는 어드민 토큰을 서명 단계에서 거부한다")
    void adminTokenIsRejectedBeforeAnyClaimIsRead() {
        String adminToken = loginAsAdmin();

        // 이 검증기는 포털 JWKS 만 본다. 어드민 토큰의 kid(admin-local)에 해당하는 공개키를 아예
        // 갖고 있지 않으므로 서명 검증에서 죽는다 — realm 클레임을 읽어보기도 전에.
        //
        // 이것이 realm 별로 JWKS 를 나눈 이유다. 한 문서에 두 키가 함께 있던 시절에는 이 토큰의
        // 서명이 통과했고, 소비 서비스가 realm 클레임을 확인해 주기를 바라는 수밖에 없었다.
        // 한 곳에서 잊으면 그대로 뚫렸다.
        assertThat(verifier.verify(adminToken)).isEmpty();
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

    private List<String> kidsOf(String realm) {
        Map<?, ?> document = http.get()
                .uri("/realms/" + realm + "/.well-known/jwks.json").retrieve().body(Map.class);
        return ((List<?>) document.get("keys")).stream()
                .map(key -> (String) asMap(key).get("kid"))
                .toList();
    }

    /** 상태코드만 본다. 기본 RestClient 는 4xx 에 예외를 던지므로 별도 클라이언트를 쓴다. */
    private int statusOf(String path) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build()
                .get().uri(path).retrieve().toBodilessEntity().getStatusCode().value();
    }

    /**
     * 어드민 realm 토큰을 얻는다. 직원은 비밀번호 계정이 없어 이메일 OTP 로 로그인하고,
     * local 프로파일은 메일을 보내지 않고 고정코드를 쓴다. 계정은 시드가 심어둔 부트스트랩 관리자다.
     */
    private String loginAsAdmin() {
        http.post().uri("/api/auth/realms/admin/login/email-otp/send-code")
                .header("Content-Type", "application/json")
                .body(Map.of("email", "admin@example.com"))
                .retrieve().toBodilessEntity();

        Map<?, ?> response = http.post().uri("/api/auth/realms/admin/login/email-otp")
                .header("Content-Type", "application/json")
                .body(Map.of("email", "admin@example.com", "verificationCode", "123456"))
                .retrieve().body(Map.class);

        return (String) ((Map<?, ?>) response.get("tokens")).get("accessToken");
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
                        .uri("/api/auth/realms/portal/login")
                        .header("Content-Type", "application/json")
                        .body(Map.of("loginId", email, "password", "pw12345678"))
                        .retrieve()
                        .body(Map.class))
                .orElseThrow(() -> new IllegalStateException("로그인 응답이 비어 있다"));
    }
}
