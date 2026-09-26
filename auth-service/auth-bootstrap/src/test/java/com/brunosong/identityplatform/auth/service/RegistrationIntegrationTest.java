package com.brunosong.identityplatform.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입 화면이 <b>로그인과 같은 끝으로</b> 이어지는지 본다. 가입이 끝나면 세션이 생기고 code 가 앱으로 간다.
 *
 * <p>화면의 폼이 보내는 값을 그대로 보낸다. 화면을 그리는 쪽은 상태코드와 문구 몇 개만 본다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class RegistrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** V9002 시드의 앱들. */
    private static final String PORTAL_CLIENT = "portal-17kqqi85h2ks";
    private static final String PORTAL_REDIRECT = "http://localhost:5173/login/callback";
    private static final String ADMIN_CLIENT = "admin-ln4efwmg0tee";
    private static final String ADMIN_REDIRECT = "http://localhost:5174/login/callback";

    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @LocalServerPort
    private int port;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    @DisplayName("포털 가입 화면은 비밀번호 가입과 인증번호 가입을 둘 다 연다")
    void portalScreenOffersBothMethods() {
        ResponseEntity<String> screen = http.get()
                .uri(URI.create("http://localhost:" + port + "/realms/portal/auth/register?" + query(PORTAL_CLIENT, PORTAL_REDIRECT)))
                .retrieve().toEntity(String.class);

        assertThat(screen.getStatusCode().value()).isEqualTo(200);
        assertThat(screen.getBody()).contains("/realms/portal/auth/register\"")
                .contains("/realms/portal/auth/register/send-code");
    }

    @Test
    @DisplayName("직원 realm 은 가입이 닫혀 있다. 화면도, 폼도, prompt=create 도 받지 않는다")
    void adminRegistrationIsClosed() {
        ResponseEntity<String> screen = http.get()
                .uri(URI.create("http://localhost:" + port + "/realms/admin/auth/register?" + query(ADMIN_CLIENT, ADMIN_REDIRECT)))
                .retrieve().toEntity(String.class);
        assertThat(screen.getStatusCode().value()).isEqualTo(404);

        assertThat(form("/realms/admin/auth/register/send-code", ADMIN_CLIENT, ADMIN_REDIRECT,
                "&email=x@example.com&name=x").getStatusCode().value()).isEqualTo(404);
        assertThat(form("/realms/admin/auth/register", ADMIN_CLIENT, ADMIN_REDIRECT,
                "&email=x@example.com&name=x&password=pw12345678").getStatusCode().value()).isEqualTo(404);

        ResponseEntity<String> create = http.get()
                .uri(URI.create("http://localhost:" + port + "/realms/admin/auth?response_type=code&prompt=create&"
                        + query(ADMIN_CLIENT, ADMIN_REDIRECT)))
                .retrieve().toEntity(String.class);
        assertThat(create.getStatusCode().value()).isEqualTo(400);
        assertThat(create.getBody()).doesNotContain("/auth/register/send-code");
    }

    @Test
    @DisplayName("직원 로그인 화면에는 회원가입 버튼이 없다")
    void adminLoginScreenHasNoSignUp() {
        ResponseEntity<String> login = http.get()
                .uri(URI.create("http://localhost:" + port + "/realms/admin/auth?response_type=code&"
                        + query(ADMIN_CLIENT, ADMIN_REDIRECT)))
                .retrieve().toEntity(String.class);

        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(login.getBody()).doesNotContain("/realms/admin/auth/register");
    }

    @Test
    @DisplayName("비밀번호로 가입하면 로그인된 채로 code 가 앱에 간다")
    void passwordRegistrationEndsInCode() {
        String email = "signup-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Void> response = form("/realms/portal/auth/register", PORTAL_CLIENT, PORTAL_REDIRECT,
                "&email=" + encode(email) + "&name=" + encode("가입자") + "&password=pw12345678");

        assertThat(response.getStatusCode().value()).isEqualTo(303);
        URI location = response.getHeaders().getLocation();
        assertThat(location.toString()).startsWith(PORTAL_REDIRECT);
        assertThat(location.getQuery()).contains("code=").contains("state=app-state");
        // 다음 앱은 이 세션으로 화면 없이 들어온다. 가입한 사람에게 로그인을 한 번 더 시키지 않는다.
        assertThat(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("AUTH_SESSION="));
    }

    @Test
    @DisplayName("이미 가입된 이메일로는 비밀번호 가입을 받지 않는다")
    void existingEmailIsRefused() {
        String email = "twice-" + UUID.randomUUID() + "@example.com";
        form("/realms/portal/auth/register", PORTAL_CLIENT, PORTAL_REDIRECT,
                "&email=" + encode(email) + "&name=a&password=pw12345678");

        ResponseEntity<String> again = http.post().uri("/realms/portal/auth/register")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(formBody(PORTAL_CLIENT, PORTAL_REDIRECT, "&email=" + encode(email) + "&name=b&password=other-pw-123"))
                .retrieve().toEntity(String.class);

        assertThat(again.getStatusCode().value()).isEqualTo(400);
        assertThat(again.getHeaders().getLocation()).isNull();
        assertThat(again.getBody()).contains("이미 가입된 이메일");
    }

    @Test
    @DisplayName("인증번호로 가입해도 로그인된 채로 code 가 앱에 간다")
    void emailRegistrationEndsInCode() {
        String email = "otp-signup-" + UUID.randomUUID() + "@example.com";
        String profile = "&email=" + encode(email) + "&name=" + encode("번호가입");

        ResponseEntity<Void> sent = form("/realms/portal/auth/register/send-code", PORTAL_CLIENT, PORTAL_REDIRECT, profile);
        assertThat(sent.getStatusCode().value()).isEqualTo(200);

        // local 프로파일은 메일을 보내지 않고 고정코드를 쓴다.
        ResponseEntity<Void> response = form("/realms/portal/auth/register/email", PORTAL_CLIENT, PORTAL_REDIRECT,
                profile + "&code=123456");

        assertThat(response.getStatusCode().value()).isEqualTo(303);
        assertThat(response.getHeaders().getLocation().getQuery()).contains("code=");
    }

    @Test
    @DisplayName("등록되지 않은 앱의 요청이면 가입 화면을 그리지 않는다")
    void unregisteredClientGetsNoScreen() {
        ResponseEntity<String> screen = http.get()
                .uri(URI.create("http://localhost:" + port + "/realms/portal/auth/register?" + query("unknown", PORTAL_REDIRECT)))
                .retrieve().toEntity(String.class);

        assertThat(screen.getStatusCode().value()).isEqualTo(400);
        assertThat(screen.getBody()).doesNotContain("/auth/register/send-code");
    }

    private ResponseEntity<Void> form(String path, String clientId, String redirectUri, String extra) {
        return http.post().uri(path)
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(formBody(clientId, redirectUri, extra))
                .retrieve().toBodilessEntity();
    }

    private static String formBody(String clientId, String redirectUri, String extra) {
        return query(clientId, redirectUri) + extra;
    }

    private static String query(String clientId, String redirectUri) {
        return "client_id=" + clientId + "&redirect_uri=" + encode(redirectUri) + "&state=app-state"
                + "&code_challenge=" + CHALLENGE + "&code_challenge_method=S256";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
