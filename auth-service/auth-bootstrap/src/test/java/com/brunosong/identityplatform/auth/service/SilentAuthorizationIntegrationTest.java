package com.brunosong.identityplatform.auth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestRegistrationOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>조용히 시도해 보는 길이 실제로 도는지</b> 본다 - {@code GET /realms/portal/auth?prompt=none}.
 *
 * <p>앱은 세션 쿠키를 읽지 못한다. 그래서 "이 브라우저가 이미 로그인해 있는가" 를 알아내려면
 * 여기로 와 보는 수밖에 없고, 와 봤을 때 <b>두 경우가 모두 리다이렉트여야</b> 앱이 코드로 분기할
 * 수 있다. 하나라도 화면이면 자동 시도는 성립하지 않는다.
 *
 * <p>그래서 이 테스트가 확인하는 것은 상태코드와 {@code Location} 헤더다. 본문은 보지 않는다.
 * 리다이렉트를 따라가지 않는 클라이언트를 쓰는 이유이기도 하다 - 따라가 버리면 5173 을 부르러
 * 나가고, 그 서버는 테스트에 없다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class SilentAuthorizationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** V9002 시드가 등록해 둔 포털 앱과 주소. 글자 그대로 대조되므로 그대로 쓴다. */
    private static final String CLIENT_ID = "portal-17kqqi85h2ks";
    private static final String REDIRECT_URI = "http://localhost:5173/login/callback";
    private static final String PASSWORD = "pw12345678";

    /** RFC 7636 부록 B 의 예시 한 쌍. 해시해서 대조하므로 짝이 맞아야 한다. */
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @LocalServerPort
    private int port;

    @Autowired
    private RegisterWithPasswordUseCase registerWithPassword;

    @Autowired
    private RequestRegistrationOtpUseCase requestRegistrationOtp;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // 4xx 에 예외를 던지지 않는다. 상태코드 자체가 확인 대상이다.
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    @DisplayName("세션이 없으면 화면이 아니라 login_required 를 달고 앱으로 돌려보낸다")
    void withoutSessionItRedirectsBackWithLoginRequired() {
        ResponseEntity<Void> response = authorize("none", null, "state-1");

        // 화면(200)이 아니라 리다이렉트여야 앱이 코드로 분기할 수 있다.
        assertThat(response.getStatusCode().value()).isEqualTo(303);

        URI location = response.getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.toString()).startsWith(REDIRECT_URI);
        assertThat(location.getQuery()).contains("error=login_required")
                // state 가 실려야 앱이 이 응답을 자기 것으로 확인할 수 있다. 실패에서도 그렇다.
                .contains("state=state-1")
                // 코드를 내줄 자리가 아니다.
                .doesNotContain("code=");
    }

    @Test
    @DisplayName("prompt 가 없으면 지금까지처럼 로그인 화면을 그린다")
    void withoutPromptItStillRendersTheLoginScreen() {
        ResponseEntity<Void> response = authorize(null, null, "state-2");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getLocation()).isNull();
    }

    @Test
    @DisplayName("세션이 있으면 prompt=none 이어도 코드를 내준다")
    void withSessionItIssuesACodeSilently() {
        String session = loginAndKeepSession();

        ResponseEntity<Void> response = authorize("none", session, "state-3");

        assertThat(response.getStatusCode().value()).isEqualTo(303);
        URI location = response.getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getQuery()).contains("code=")
                .contains("state=state-3")
                .doesNotContain("error=");
    }

    @Test
    @DisplayName("아는 prompt 는 none 하나다. 나머지는 앱으로 돌려보내지 않고 여기서 끝낸다")
    void otherPromptValuesAreRefused() {
        ResponseEntity<Void> response = authorize("login", null, "state-4");

        // 조용히 무시하면 재인증을 요구한 쪽이 다시 물었다고 믿는다. 그래서 거절한다.
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getLocation()).isNull();
    }

    @Test
    @DisplayName("prompt=create 면 로그인 화면 대신 가입 화면을 그린다")
    void createPromptRendersRegistrationScreen() {
        ResponseEntity<String> response = http.get()
                .uri(URI.create("http://localhost:" + port + "/realms/portal/auth?response_type=code"
                        + "&client_id=" + CLIENT_ID + "&redirect_uri=" + REDIRECT_URI
                        + "&state=state-c&code_challenge=" + CHALLENGE + "&code_challenge_method=S256&prompt=create"))
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("/realms/portal/auth/register/send-code");
    }

    @Test
    @DisplayName("발급자 문서가 적은 prompt 값과 실제로 받는 값이 같다")
    void discoveryMatchesWhatTheEndpointAccepts() {
        Map<String, Object> document = http.get()
                .uri("/realms/portal/.well-known/openid-configuration")
                .retrieve().body(new ParameterizedTypeReference<>() { });

        assertThat(document).containsEntry("prompt_values_supported", List.of("none", "create"))
                // 재발급이 토큰 엔드포인트로 들어왔으니 문서에도 적혀 있어야 한다.
                .containsEntry("grant_types_supported", List.of("authorization_code", "refresh_token"));
    }
    @Test
    @DisplayName("코드를 바꾸면 토큰 둘이 모두 본문으로 나온다")
    void bothTokensComeInTheBody() {
        String session = loginAndKeepSession();
        String code = codeFrom(authorize("none", session, "state-5"));

        ResponseEntity<String> response = exchange(code);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("access_token").contains("refresh_token");

        // 쿠키는 나가지 않는다. 한때 refresh 토큰만 HttpOnly 쿠키로 내보냈는데,
        // 로그인 방식마다 토큰이 있는 자리가 갈리는 값을 치르고 있었다.
        assertThat(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("REFRESH_TOKEN="));
    }

    @Test
    @DisplayName("openid 를 달라고 했으면 id_token 이 붙는다. aud 는 앱이고 nonce 는 요청한 값이다")
    void openIdScopeAddsIdToken() {
        String session = loginAndKeepSession();
        String code = codeFrom(authorize("none", session, "state-8", "&scope=openid&nonce=n-0S6_WzA2Mj"));

        ResponseEntity<String> response = exchange(code);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String idToken = fieldOf(response, "id_token");
        String claims = payloadOf(idToken);
        assertThat(claims).contains("\"aud\":\"" + CLIENT_ID + "\"")
                .contains("\"nonce\":\"n-0S6_WzA2Mj\"")
                .contains("\"iss\":\"http://localhost:8080/realms/portal\"");
        // sub 는 access 토큰과 같은 사람이어야 한다.
        assertThat(subOf(claims)).isEqualTo(subOf(payloadOf(fieldOf(response, "access_token"))));
    }

    @Test
    @DisplayName("openid 가 없으면 id_token 도 없고, 재발급에서도 내지 않는다")
    void noIdTokenWithoutOpenIdOrOnRefresh() {
        String session = loginAndKeepSession();

        ResponseEntity<String> withoutOpenId = exchange(codeFrom(authorize("none", session, "state-9")));
        assertThat(withoutOpenId.getBody()).doesNotContain("id_token");

        String code = codeFrom(authorize("none", session, "state-10", "&scope=openid"));
        ResponseEntity<String> renewed = refreshWith(refreshTokenOf(exchange(code)));
        assertThat(renewed.getBody()).contains("access_token").doesNotContain("id_token");
    }

    @Test
    @DisplayName("재발급도 같은 엔드포인트다. grant_type 이 가른다")
    void refreshUsesTheSameEndpoint() {
        String session = loginAndKeepSession();
        String code = codeFrom(authorize("none", session, "state-6"));

        String issued = refreshTokenOf(exchange(code));
        ResponseEntity<String> renewed = refreshWith(issued);

        assertThat(renewed.getStatusCode().value()).isEqualTo(200);
        assertThat(renewed.getBody()).contains("access_token").contains("refresh_token");

        // 값은 반드시 달라진다. 토큰마다 jti 가 다르기 때문이다. 전에는 sub, iss, type, iat, exp 만
        // 있어서 같은 초에 재발급하면 글자까지 같은 토큰이 나왔다.
        assertThat(refreshTokenOf(renewed)).isNotBlank().isNotEqualTo(issued);
    }

    @Test
    @DisplayName("이미 쓴 refresh 토큰이 다시 오면 그 계보가 통째로 끊긴다")
    void reusedRefreshTokenRevokesTheWholeChain() {
        String session = loginAndKeepSession();
        String code = codeFrom(authorize("none", session, "state-7"));

        String issued = refreshTokenOf(exchange(code));
        String renewed = refreshTokenOf(refreshWith(issued));

        // 방금 쓴 토큰을 다시 낸다. 훔친 쪽이 쓴 것인지 진짜 사용자가 뒤늦게 쓴 것인지 가릴 수 없다.
        ResponseEntity<String> reused = refreshWith(issued);
        assertThat(reused.getStatusCode().value()).isEqualTo(400);
        // 기계가 읽는 자리라 오류 이름도 명세의 것을 쓴다.
        assertThat(reused.getBody()).contains("invalid_grant");

        // 그래서 직전에 정상으로 받은 토큰까지 함께 죽는다. 계보를 통째로 끊는다는 것이 이 뜻이다.
        assertThat(refreshWith(renewed).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("refresh 토큰 없이 부르면 거절한다")
    void refreshWithoutATokenIsRefused() {
        ResponseEntity<String> response = form("grant_type=refresh_token");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("invalid_grant");
    }

    @Test
    @DisplayName("모르는 grant_type 은 아는 둘에 걸리지 않고 따로 거절된다")
    void unknownGrantTypeIsRefused() {
        ResponseEntity<String> response = form("grant_type=password&username=a&password=b");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("unsupported_grant_type");
    }

    private ResponseEntity<String> exchange(String code) {
        return form("grant_type=authorization_code&code=" + code
                + "&client_id=" + CLIENT_ID
                + "&redirect_uri=" + REDIRECT_URI
                + "&code_verifier=" + VERIFIER);
    }

    private ResponseEntity<String> refreshWith(String refreshToken) {
        return form("grant_type=refresh_token&refresh_token=" + refreshToken
                + "&client_id=" + CLIENT_ID);
    }

    private ResponseEntity<String> form(String body) {
        return http.post().uri("/realms/portal/token")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(body)
                .retrieve().toEntity(String.class);
    }

    /** 응답 본문에서 refresh 토큰만 꺼낸다. JSON 하나 읽자고 매퍼를 들이지 않는다. */
    private static String refreshTokenOf(ResponseEntity<String> response) {
        return fieldOf(response, "refresh_token");
    }

    private static String fieldOf(ResponseEntity<String> response, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\s*:\s*\"([^\"]+)\"")
                .matcher(String.valueOf(response.getBody()));
        if (!matcher.find()) {
            throw new IllegalStateException("본문에 " + field + " 이 없다: " + response.getBody());
        }
        return matcher.group(1);
    }

    /** 서명은 보지 않고 가운데 조각만 푼다. 무엇이 실렸는지만 본다. */
    private static String payloadOf(String jwt) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    private static String subOf(String claims) {
        return claims.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
    }

    private static String codeFrom(ResponseEntity<Void> response) {
        URI location = response.getHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getQuery().replaceAll(".*code=([^&]+).*", "$1");
    }

    /**
     * 진짜로 로그인해서 세션 쿠키를 받아 온다.
     *
     * <p>쿠키를 손으로 만들지 않는 것이 요점이다. 세션 아이디는 서버가 정하는 값이고, 이 테스트가
     * 보려는 것은 "그 쿠키가 다음 인가 요청에 실려 오면 화면이 안 뜬다" 이기 때문이다.
     */
    private String loginAndKeepSession() {
        String email = "silent-" + UUID.randomUUID() + "@example.com";
        requestRegistrationOtp.request(Realm.PORTAL, email);   // local 은 고정코드 123456
        registerWithPassword.register(new RegisterWithPasswordCommand(
                Realm.PORTAL, email, "Tester", null, email, PASSWORD, "123456"));

        ResponseEntity<Void> response = http.post()
                .uri("/realms/portal/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body("client_id=" + CLIENT_ID
                        + "&redirect_uri=" + REDIRECT_URI
                        + "&code_challenge=" + CHALLENGE
                        + "&code_challenge_method=S256"
                        + "&loginId=" + email
                        + "&password=" + PASSWORD)
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(303);
        return sessionCookieOf(response);
    }

    /** Set-Cookie 에서 AUTH_SESSION 값만 꺼낸다. */
    private static String sessionCookieOf(ResponseEntity<Void> response) {
        List<String> cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        String prefix = "AUTH_SESSION=";
        return cookies.stream()
                .filter(cookie -> cookie.startsWith(prefix))
                // 값 뒤로 Path, HttpOnly 같은 속성이 따라붙는다. 첫 세미콜론까지가 값이다.
                .map(cookie -> cookie.substring(prefix.length(), cookie.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("세션 쿠키가 없다: " + cookies));
    }

    private ResponseEntity<Void> authorize(String prompt, String sessionId, String state) {
        return authorize(prompt, sessionId, state, "");
    }

    /** {@code extraQuery} 는 scope, nonce 처럼 이 테스트가 가끔만 싣는 값이다. {@code &} 부터 적는다. */
    private ResponseEntity<Void> authorize(String prompt, String sessionId, String state, String extraQuery) {
        StringBuilder uri = new StringBuilder("/realms/portal/auth")
                .append("?response_type=code")
                .append("&client_id=").append(CLIENT_ID)
                .append("&redirect_uri=").append(REDIRECT_URI)
                .append("&state=").append(state)
                .append("&code_challenge=").append(CHALLENGE)
                .append("&code_challenge_method=S256")
                .append(extraQuery);
        if (prompt != null) {
            uri.append("&prompt=").append(prompt);
        }

        RestClient.RequestHeadersSpec<?> request = http.get().uri(uri.toString());
        if (sessionId != null) {
            request = request.header(HttpHeaders.COOKIE, "AUTH_SESSION=" + sessionId);
        }
        return request.retrieve().toBodilessEntity();
    }
}
