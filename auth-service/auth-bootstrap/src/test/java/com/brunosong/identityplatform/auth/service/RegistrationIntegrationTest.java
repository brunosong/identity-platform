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
    @DisplayName("포털 가입 화면은 인증번호부터 받는다")
    void portalScreenStartsWithCode() {
        ResponseEntity<String> screen = http.get()
                .uri(URI.create("http://localhost:" + port + "/realms/portal/auth/register?" + query(PORTAL_CLIENT, PORTAL_REDIRECT)))
                .retrieve().toEntity(String.class);

        assertThat(screen.getStatusCode().value()).isEqualTo(200);
        // 첫 걸음에는 비밀번호 칸이 없다. 비밀번호는 번호를 받은 뒤에 정한다.
        assertThat(screen.getBody()).contains("/realms/portal/auth/register/send-code")
                .doesNotContain("name=\"password\"");
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
    @DisplayName("번호를 받고 비밀번호를 정해 가입하면 로그인된 채로 code 가 앱에 간다")
    void passwordRegistrationEndsInCode() {
        String profile = profileOf("signup-" + UUID.randomUUID() + "@example.com", "가입자");
        form("/realms/portal/auth/register/send-code", PORTAL_CLIENT, PORTAL_REDIRECT, profile);

        // local 프로파일은 메일을 보내지 않고 고정코드를 쓴다.
        ResponseEntity<Void> response = form("/realms/portal/auth/register", PORTAL_CLIENT, PORTAL_REDIRECT,
                profile + "&code=123456&password=pw12345678");

        assertThat(response.getStatusCode().value()).isEqualTo(303);
        URI location = response.getHeaders().getLocation();
        assertThat(location.toString()).startsWith(PORTAL_REDIRECT);
        assertThat(location.getQuery()).contains("code=").contains("state=app-state");
        // 다음 앱은 이 세션으로 화면 없이 들어온다. 가입한 사람에게 로그인을 한 번 더 시키지 않는다.
        assertThat(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("AUTH_SESSION="));
    }

    @Test
    @DisplayName("비밀번호를 비우면 인증번호로만 로그인하는 계정이 되고, 역시 로그인된 채로 돌아간다")
    void emailOnlyRegistrationEndsInCode() {
        String profile = profileOf("otp-signup-" + UUID.randomUUID() + "@example.com", "번호가입");
        ResponseEntity<Void> sent = form("/realms/portal/auth/register/send-code", PORTAL_CLIENT, PORTAL_REDIRECT, profile);
        assertThat(sent.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<Void> response = form("/realms/portal/auth/register", PORTAL_CLIENT, PORTAL_REDIRECT,
                profile + "&code=123456");

        assertThat(response.getStatusCode().value()).isEqualTo(303);
        assertThat(response.getHeaders().getLocation().getQuery()).contains("code=");
    }

    @Test
    @DisplayName("번호를 받지 않았으면 비밀번호가 있어도 가입되지 않는다")
    void passwordAloneDoesNotRegister() {
        // 확인하지 않은 주소로 계정을 만들 수 없다. 남의 주소일 수 있다.
        ResponseEntity<Void> response = form("/realms/portal/auth/register", PORTAL_CLIENT, PORTAL_REDIRECT,
                profileOf("nocode-" + UUID.randomUUID() + "@example.com", "a") + "&code=123456&password=pw12345678");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getLocation()).isNull();
    }

    @Test
    @DisplayName("이미 가입된 이메일은 번호가 나가지 않아 가입되지 않고, 가입 여부도 드러나지 않는다")
    void existingEmailIsRefusedWithoutSayingSo() {
        String profile = profileOf("twice-" + UUID.randomUUID() + "@example.com", "a");
        form("/realms/portal/auth/register/send-code", PORTAL_CLIENT, PORTAL_REDIRECT, profile);
        form("/realms/portal/auth/register", PORTAL_CLIENT, PORTAL_REDIRECT, profile + "&code=123456");

        // 같은 주소로 다시 가입해 본다. 가입용 번호는 가입된 주소로 나가지 않는다.
        form("/realms/portal/auth/register/send-code", PORTAL_CLIENT, PORTAL_REDIRECT, profile);
        ResponseEntity<String> again = http.post().uri("/realms/portal/auth/register")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(formBody(PORTAL_CLIENT, PORTAL_REDIRECT, profile + "&code=123456&password=other-pw-123"))
                .retrieve().toEntity(String.class);

        assertThat(again.getStatusCode().value()).isEqualTo(401);
        assertThat(again.getHeaders().getLocation()).isNull();
        // "이미 가입된 이메일" 이라고 말하면 주소를 넣어보는 것만으로 누가 가입했는지 훑을 수 있다.
        assertThat(again.getBody()).doesNotContain("이미 가입된");
    }

    @Test
    @DisplayName("가입만 하는 입구는 완료 화면을 보여주고, 등록된 돌아갈 주소를 링크로만 건다. 로그인은 안 된다")
    void signUpOnlyShowsCompletion() {
        String email = "signup-only-" + UUID.randomUUID() + "@example.com";
        String app = "client_id=" + PORTAL_CLIENT + "&redirect_uri=" + encode("http://localhost:5173/");

        ResponseEntity<String> screen = http.get()
                .uri(URI.create("http://localhost:" + port + "/realms/portal/register?" + app))
                .retrieve().toEntity(String.class);
        assertThat(screen.getStatusCode().value()).isEqualTo(200);
        assertThat(screen.getBody()).contains("/realms/portal/register/send-code");

        post("/realms/portal/register/send-code", app + profileOf(email, "가입만"));
        ResponseEntity<String> done = post("/realms/portal/register",
                app + profileOf(email, "가입만") + "&code=123456&password=pw12345678");

        assertThat(done.getStatusCode().value()).isEqualTo(200);
        assertThat(done.getHeaders().getLocation()).isNull();
        assertThat(done.getBody()).contains("가입을 완료했습니다").contains("href=\"http://localhost:5173/\"");
        // 세션을 심지 않는다. 앱으로 돌아가 로그인을 다시 한다.
        assertThat(done.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("AUTH_SESSION="));

        // 가입은 제대로 됐다. 정한 비밀번호로 로그인하면 토큰이 나온다.
        assertThat(new CodeFlowLogin(port).portalWithPassword(email, "pw12345678").accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("가입만 하는 입구에 등록되지 않은 주소를 적으면 링크를 보이지 않는다. 가입은 막지 않는다")
    void signUpOnlyHidesUnregisteredReturnUri() {
        String email = "signup-evil-" + UUID.randomUUID() + "@example.com";
        String app = "client_id=" + PORTAL_CLIENT + "&redirect_uri=" + encode("https://evil.example.com/");

        post("/realms/portal/register/send-code", app + profileOf(email, "a"));
        ResponseEntity<String> done = post("/realms/portal/register", app + profileOf(email, "a") + "&code=123456");

        assertThat(done.getStatusCode().value()).isEqualTo(200);
        assertThat(done.getBody()).contains("가입을 완료했습니다").doesNotContain("evil.example.com");
    }

    @Test
    @DisplayName("직원 realm 에는 가입만 하는 입구도 없다")
    void signUpOnlyIsClosedForAdmin() {
        ResponseEntity<String> screen = http.get()
                .uri(URI.create("http://localhost:" + port + "/realms/admin/register"))
                .retrieve().toEntity(String.class);
        assertThat(screen.getStatusCode().value()).isEqualTo(404);
        assertThat(post("/realms/admin/register/send-code", "email=x@example.com&name=x").getStatusCode().value())
                .isEqualTo(404);
    }

    private ResponseEntity<String> post(String path, String body) {
        return http.post().uri(path)
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(body)
                .retrieve().toEntity(String.class);
    }

    private static String profileOf(String email, String name) {
        return "&email=" + encode(email) + "&name=" + encode(name);
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
