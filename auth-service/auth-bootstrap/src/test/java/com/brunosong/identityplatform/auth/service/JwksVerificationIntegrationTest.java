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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
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

    /**
     * 발급자는 <b>식별자</b>라 문자열로 대조하고, JWKS 주소는 <b>주소</b>라 실제로 접속한다.
     * 이 테스트는 랜덤 포트로 뜨므로 둘이 갈린다 — 실제 배포에서는 같은 호스트라 주소가 유도된다.
     */
    private static final String PORTAL_ISSUER = "http://localhost:8080/realms/portal";

    /** 쿨다운 때문에 클래스 전체에서 한 번만 로그인한다. 컨텍스트가 같으니 토큰도 그대로 쓸 수 있다. */
    private static String cachedAdminToken;

    private RestClient http;
    /** 소비 서비스가 갖게 될 검증기. auth 의 내부 빈이 아니라 JWKS 주소만 알고 있다. */
    private AuthTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
        verifier = new AuthTokenVerifier(portalKeys(), PORTAL_ISSUER);
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
        assertThat(token.issuer()).isEqualTo(PORTAL_ISSUER);
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

    @Test
    @DisplayName("토큰은 realm 을 클레임으로 들고 다니지 않는다")
    void tokenCarriesNoRealmClaim() {
        String email = "claims-" + UUID.randomUUID() + "@example.com";
        registerCustomer(email);

        // realm 마다 서명키가 달라서 "어느 키로 검증됐는지" 가 곧 realm 이다. 클레임으로 한 번 더
        // 적으면 토큰이 스스로 하는 주장이 생기고, 검증하는 쪽이 그걸 대조하기를 바라게 된다.
        assertThat(payloadOf(login(email)))
                .doesNotContainKey("realm")
                // realm 은 표준 자리인 iss 안에 있다.
                .containsEntry("iss", PORTAL_ISSUER)
                .containsEntry("type", "access")
                .containsKeys("sub", "authLs", "rbacRev", "exp");
    }

    @Test
    @DisplayName("다른 배포가 발급한 토큰은 서명이 맞아도 거부된다")
    void tokenFromAnotherDeploymentIsRejected() {
        String email = "issuer-" + UUID.randomUUID() + "@example.com";
        registerCustomer(email);
        String accessToken = login(email);

        // 이 검증기는 같은 JWKS 를 보므로 서명은 통과한다. realm 도 같고 클레임도 같다.
        // 다른 것은 발급자 이름 하나뿐이다 — staging 토큰이 prod 를 여는 상황이 정확히 이 모양이다.
        AuthTokenVerifier otherDeployment =
                new AuthTokenVerifier(portalKeys(), "https://auth.example.com/realms/portal");

        assertThat(otherDeployment.verify(accessToken)).isEmpty();
        assertThat(verifier.verify(accessToken)).isPresent();
    }

    @Test
    @DisplayName("realm 이 붙지 않은 옛 경로는 없다")
    void realmLessEndpointsAreGone() {
        assertThat(statusOf("/api/auth/my-permissions")).isEqualTo(404);
        assertThat(postStatusOf("/api/auth/logout", null)).isEqualTo(404);
        assertThat(postJson("/api/auth/customer/register", selfRegisterBody(), null)).isEqualTo(404);
        assertThat(postJson("/api/auth/employee/register", adminUserBody(), null)).isEqualTo(404);
    }

    @Test
    @DisplayName("셀프 가입은 realm 이 여는 realm 에서만 열린다")
    void selfRegistrationIsARealmSetting() {
        assertThat(postJson("/api/auth/realms/portal/register", selfRegisterBody(), null)).isEqualTo(201);

        // 어드민에서 가입이 열리면 아무나 자기 자신을 직원으로 만든다. 403 이 아니라 404 인 이유는
        // "여기에도 가입 API 가 있긴 한데 막혀 있다" 를 알려줄 이유가 없기 때문이다.
        assertThat(postJson("/api/auth/realms/admin/register", selfRegisterBody(), null)).isEqualTo(404);
        assertThat(postJson("/api/auth/realms/martian/register", selfRegisterBody(), null)).isEqualTo(404);
    }

    @Test
    @DisplayName("관리자 계정 생성은 대상 realm 이 경로에, 호출자 realm 이 토큰에 있다")
    void adminUserCreationSeparatesTargetFromCaller() {
        // 대상 realm 을 먼저 본다 — 포털 계정을 대신 만드는 유스케이스는 아직 없다.
        assertThat(postJson("/api/auth/admin/realms/portal/users", adminUserBody(), null)).isEqualTo(404);
        // 대상은 맞지만 호출자를 밝히지 않았다.
        assertThat(postJson("/api/auth/admin/realms/admin/users", adminUserBody(), null)).isEqualTo(401);
        // 어드민 토큰이면 통과한다.
        assertThat(postJson("/api/auth/admin/realms/admin/users", adminUserBody(), loginAsAdmin()))
                .isEqualTo(201);
    }

    @Test
    @DisplayName("경로에 남의 realm 을 적어도 그 realm 이 되지 않는다")
    void pathCannotSpoofRealm() {
        String email = "spoof-" + UUID.randomUUID() + "@example.com";
        registerCustomer(email);
        String portalToken = login(email);

        Map<String, Object> mine = getWithToken("/api/auth/realms/portal/my-permissions", portalToken);
        assertThat(mine.get("realm")).isEqualTo("PORTAL");

        // 같은 토큰을 어드민 경로에 내밀면, 그 요청은 어드민 공개키로 검증된다. 경로는 realm 을
        // 주장하는 값이 아니라 검증 키를 고르는 값이라, 잘못 적으면 통과하지 못한다.
        Map<String, Object> spoofed = getWithToken("/api/auth/realms/admin/my-permissions", portalToken);
        assertThat(spoofed.get("realm")).isNull();
        assertThat((List<?>) spoofed.get("permissions")).isEmpty();
    }

    @Test
    @DisplayName("로그아웃도 realm 경로 위에 있고, 남의 realm 토큰은 통하지 않는다")
    void logoutIsRealmScoped() {
        String email = "logout-" + UUID.randomUUID() + "@example.com";
        registerCustomer(email);
        String portalToken = login(email);

        assertThat(postStatusOf("/api/auth/realms/portal/logout", portalToken)).isEqualTo(204);
        assertThat(postStatusOf("/api/auth/realms/admin/logout", portalToken)).isEqualTo(401);
        assertThat(postStatusOf("/api/auth/realms/portal/logout", null)).isEqualTo(401);
        assertThat(postStatusOf("/api/auth/realms/martian/logout", portalToken)).isEqualTo(404);
    }

    private int postJson(String path, Map<String, Object> body, String bearerToken) {
        RestClient.RequestBodySpec request = lenient().post().uri(path)
                .header("Content-Type", "application/json");
        if (bearerToken != null) {
            request = request.header("Authorization", "Bearer " + bearerToken);
        }
        return request.body(body).retrieve().toBodilessEntity().getStatusCode().value();
    }

    private static Map<String, Object> selfRegisterBody() {
        String email = "reg-" + UUID.randomUUID() + "@example.com";
        return Map.of("email", email, "password", "pw12345678", "name", "Tester");
    }

    private static Map<String, Object> adminUserBody() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return Map.of(
                "employeeId", "E" + suffix,
                "name", "Operator",
                "email", "op-" + suffix + "@example.com");
    }

    /** 소비 서비스가 갖게 될 키 소스. 주소는 실제로 뜬 포트를 쓴다. */
    private JwksKeySource portalKeys() {
        return new JwksKeySource(
                RestClient.create(),
                "http://localhost:" + port + "/realms/portal/.well-known/jwks.json",
                Duration.ofMinutes(10));
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
        return lenient().get().uri(path).retrieve().toBodilessEntity().getStatusCode().value();
    }

    private int postStatusOf(String path, String bearerToken) {
        RestClient.RequestBodySpec request = lenient().post().uri(path);
        if (bearerToken != null) {
            request = request.header("Authorization", "Bearer " + bearerToken);
        }
        return request.retrieve().toBodilessEntity().getStatusCode().value();
    }

    private Map<String, Object> getWithToken(String path, String bearerToken) {
        return asMap(http.get().uri(path)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve().body(Map.class));
    }

    private RestClient lenient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    /** 서명은 확인하지 않고 payload 만 편다 — 무엇이 실려 나가는지 보는 용도다. */
    private static Map<String, Object> payloadOf(String jwt) {
        byte[] json = Base64.getUrlDecoder().decode(jwt.split("\\.")[1]);
        try {
            return asMap(new ObjectMapper().readValue(new String(json, StandardCharsets.UTF_8), Map.class));
        } catch (Exception e) {
            throw new IllegalStateException("토큰 payload 를 읽지 못했다", e);
        }
    }

    /**
     * 어드민 realm 토큰을 얻는다. 직원은 비밀번호 계정이 없어 이메일 OTP 로 로그인하고,
     * local 프로파일은 메일을 보내지 않고 고정코드를 쓴다. 계정은 시드가 심어둔 부트스트랩 관리자다.
     *
     * <p><b>한 번만 로그인하고 재사용한다.</b> OTP 발송에는 재발송 쿨다운이 있어
     * ({@code RequestEmailOtpService.RESEND_COOLDOWN_SECONDS}) 짧은 간격의 두 번째 요청은 조용히
     * 무시된다 — 첫 챌린지는 이미 소비됐으므로 그 다음 검증은 401 이 된다. 실제 사용자에게는 이것이
     * 올바른 동작(무차별 발송 방지)이라, 테스트 쪽이 맞춰야 한다.
     */
    private String loginAsAdmin() {
        if (cachedAdminToken != null) {
            return cachedAdminToken;
        }
        http.post().uri("/api/auth/realms/admin/login/email-otp/send-code")
                .header("Content-Type", "application/json")
                .body(Map.of("email", "admin@example.com"))
                .retrieve().toBodilessEntity();

        Map<?, ?> response = http.post().uri("/api/auth/realms/admin/login/email-otp")
                .header("Content-Type", "application/json")
                .body(Map.of("email", "admin@example.com", "verificationCode", "123456"))
                .retrieve().body(Map.class);

        cachedAdminToken = (String) ((Map<?, ?>) response.get("tokens")).get("accessToken");
        return cachedAdminToken;
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
