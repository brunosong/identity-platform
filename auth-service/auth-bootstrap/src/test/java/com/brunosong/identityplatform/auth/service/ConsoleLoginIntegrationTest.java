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
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 화면이 MASTER 로그인을 거쳐야 열리는지 본다.
 *
 * <p>브라우저가 하는 일을 손으로 밟는다. 화면을 열면 로그인으로 보내지고, 로그인 폼을 내면 code 가
 * 콜백으로 오고, 콜백이 토큰을 쿠키로 심은 뒤 원래 화면으로 돌려보낸다. 리다이렉트를 따라가지 않는 클라이언트를
 * 쓰는 이유는 매 걸음의 {@code Location} 을 보기 위해서다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class ConsoleLoginIntegrationTest {

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

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    @DisplayName("로그인하지 않았으면 MASTER 로그인으로 보낸다")
    void redirectsToMasterLogin() {
        ResponseEntity<Void> response = get("/page/roles", null);

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        URI location = response.getHeaders().getLocation();
        assertThat(location.getPath()).isEqualTo("/realms/master/auth");
        Map<String, String> query = queryOf(location);
        assertThat(query).containsEntry("client_id", "auth-console")
                .containsEntry("code_challenge_method", "S256")
                .containsKeys("state", "code_challenge");
    }

    @Test
    @DisplayName("운영 화면 밖의 주소도, 루트도 로그인 없이는 로그인으로 보낸다")
    void guardsWholeServer() {
        assertThat(get("/", null).getHeaders().getLocation().getPath()).isEqualTo("/realms/master/auth");
        assertThat(get("/anything", null).getHeaders().getLocation().getPath()).isEqualTo("/realms/master/auth");
    }

    @Test
    @DisplayName("화면이 아닌 요청은 로그인을 시작하지 않는다. 로그인 쿠키를 덮으면 진행 중인 로그인이 깨진다")
    void nonPageRequestDoesNotStartLogin() {
        ResponseEntity<Void> response = http.get().uri("/favicon.ico")
                .header(HttpHeaders.ACCEPT, "image/avif,image/webp,*/*")
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("CONSOLE_LOGIN="));
    }

    @Test
    @DisplayName("로그인과 API 는 막지 않는다. 막으면 로그인하러 간 곳이 다시 로그인으로 보낸다")
    void leavesLoginAndApiOpen() {
        assertThat(get("/realms/master/.well-known/openid-configuration", null).getStatusCode().value())
                .isEqualTo(200);
        // API 는 자기 검사로 401 을 준다. 로그인 화면으로 302 를 보내지 않는다.
        assertThat(get("/api/admin/rbac/roles?realm=ADMIN", null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("MASTER 관리자로 로그인하면 토큰이 쿠키로 내려오고, 그 쿠키로 가려던 화면이 열린다")
    void loginThenOpen() {
        ResponseEntity<Void> start = get("/page/roles?realm=PORTAL", null);
        String loginCookie = cookie(start, "CONSOLE_LOGIN");
        assertThat(setCookie(start, "CONSOLE_LOGIN")).contains("HttpOnly", "Path=/page/login/callback");
        Map<String, String> query = queryOf(start.getHeaders().getLocation());

        URI callback = login(query).getHeaders().getLocation();
        ResponseEntity<Void> finished = get(callback.getRawPath() + "?" + callback.getRawQuery(), loginCookie);

        assertThat(finished.getStatusCode().value()).isEqualTo(302);
        assertThat(finished.getHeaders().getLocation().toString()).endsWith("/page/roles?realm=PORTAL");
        assertThat(setCookie(finished, "CONSOLE_ACCESS")).contains("HttpOnly", "Path=/;");
        assertThat(setCookie(finished, "CONSOLE_REFRESH")).contains("HttpOnly", "Path=/;");
        // 로그인 쿠키는 한 번 쓰고 지운다.
        assertThat(setCookie(finished, "CONSOLE_LOGIN")).contains("Max-Age=0");

        assertThat(get("/page/roles?realm=PORTAL", cookie(finished, "CONSOLE_ACCESS")).getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("state 가 다르면 교환하지 않고 토큰 쿠키도 내려주지 않는다")
    void rejectsForeignState() {
        ResponseEntity<Void> start = get("/page/roles", null);
        String loginCookie = cookie(start, "CONSOLE_LOGIN");
        Map<String, String> query = queryOf(start.getHeaders().getLocation());

        URI callback = login(query).getHeaders().getLocation();
        String code = queryOf(callback).get("code");
        ResponseEntity<Void> finished = get("/page/login/callback?code=" + code + "&state=someone-else", loginCookie);

        assertThat(finished.getStatusCode().value()).isEqualTo(400);
        assertThat(finished.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("CONSOLE_ACCESS="));
    }

    @Test
    @DisplayName("refresh 토큰을 access 쿠키 자리에 넣으면 화면을 열지 않는다")
    void rejectsRefreshAsAccess() {
        ResponseEntity<Void> start = get("/page/roles", null);
        Map<String, String> query = queryOf(start.getHeaders().getLocation());
        URI callback = login(query).getHeaders().getLocation();
        ResponseEntity<Void> finished = get(callback.getRawPath() + "?" + callback.getRawQuery(),
                cookie(start, "CONSOLE_LOGIN"));
        String refresh = cookie(finished, "CONSOLE_REFRESH").substring("CONSOLE_REFRESH=".length());

        assertThat(get("/page/roles", "CONSOLE_ACCESS=" + refresh).getStatusCode().value()).isEqualTo(302);
    }

    @Test
    @DisplayName("쿠키의 토큰이 위조됐으면 화면을 열지 않는다")
    void rejectsForgedAccessCookie() {
        ResponseEntity<Void> response = get("/page/roles", "CONSOLE_ACCESS=eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/realms/master/auth");
    }

    /** 로그인 화면의 폼이 보내는 값 그대로. 인가 요청의 값을 숨은 칸으로 들고 간다. */
    private ResponseEntity<Void> login(Map<String, String> query) {
        return http.post().uri("/realms/master/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body("client_id=" + query.get("client_id")
                        + "&redirect_uri=" + encode(query.get("redirect_uri"))
                        + "&state=" + query.get("state")
                        + "&code_challenge=" + query.get("code_challenge")
                        + "&code_challenge_method=S256"
                        + "&loginId=admin&password=admin")
                .retrieve().toBodilessEntity();
    }

    private ResponseEntity<Void> get(String path, String cookie) {
        // 주소창 이동처럼 HTML 을 달라고 한다. 로그인으로 보내는 것은 화면 요청뿐이다.
        var request = http.get().uri(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,*/*;q=0.8");
        if (cookie != null) {
            request = request.header(HttpHeaders.COOKIE, cookie);
        }
        return request.retrieve().toBodilessEntity();
    }

    /** 응답이 심은 쿠키 한 줄 전체. 속성까지 본다. */
    private static String setCookie(ResponseEntity<Void> response, String name) {
        return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(name + " 쿠키가 없다"));
    }

    /** 다음 요청의 Cookie 헤더에 실을 "이름=값". */
    private static String cookie(ResponseEntity<Void> response, String name) {
        String line = setCookie(response, name);
        return line.substring(0, line.indexOf(';'));
    }

    private static Map<String, String> queryOf(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> java.net.URLDecoder.decode(e.getValue(), StandardCharsets.UTF_8)));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
