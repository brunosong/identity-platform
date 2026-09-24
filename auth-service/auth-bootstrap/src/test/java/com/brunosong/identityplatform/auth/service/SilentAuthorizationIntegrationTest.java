package com.brunosong.identityplatform.auth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /** V9003 시드가 등록해 둔 앱과 주소. 글자 그대로 대조되므로 그대로 쓴다. */
    private static final String CLIENT_ID = "portal";
    private static final String REDIRECT_URI = "http://localhost:5173/login/callback";
    private static final String PASSWORD = "pw12345678";

    /** 이 테스트는 코드를 토큰으로 바꾸지 않으므로 PKCE 원본이 필요 없다. 모양만 맞으면 된다. */
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @LocalServerPort
    private int port;

    @Autowired
    private RegisterWithPasswordUseCase registerWithPassword;

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
    @DisplayName("발급자 문서가 적은 prompt 값과 실제로 받는 값이 같다")
    void discoveryMatchesWhatTheEndpointAccepts() {
        Map<String, Object> document = http.get()
                .uri("/realms/portal/.well-known/openid-configuration")
                .retrieve().body(new ParameterizedTypeReference<>() { });

        assertThat(document).containsEntry("prompt_values_supported", List.of("none"));
    }

    /**
     * 진짜로 로그인해서 세션 쿠키를 받아 온다.
     *
     * <p>쿠키를 손으로 만들지 않는 것이 요점이다. 세션 아이디는 서버가 정하는 값이고, 이 테스트가
     * 보려는 것은 "그 쿠키가 다음 인가 요청에 실려 오면 화면이 안 뜬다" 이기 때문이다.
     */
    private String loginAndKeepSession() {
        String email = "silent-" + UUID.randomUUID() + "@example.com";
        registerWithPassword.register(new RegisterWithPasswordCommand(
                Realm.PORTAL, email, "Tester", null, email, PASSWORD));

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
        StringBuilder uri = new StringBuilder("/realms/portal/auth")
                .append("?response_type=code")
                .append("&client_id=").append(CLIENT_ID)
                .append("&redirect_uri=").append(REDIRECT_URI)
                .append("&state=").append(state)
                .append("&code_challenge=").append(CHALLENGE)
                .append("&code_challenge_method=S256");
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
