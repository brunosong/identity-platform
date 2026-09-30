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
 * 콜백으로 오고, 콜백이 세션을 만든 뒤 원래 화면으로 돌려보낸다. 리다이렉트를 따라가지 않는 클라이언트를
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
    @DisplayName("MASTER 관리자로 로그인하면 가려던 화면으로 돌아가고 그 화면이 열린다")
    void loginThenOpen() {
        ResponseEntity<Void> start = get("/page/roles?realm=PORTAL", null);
        String before = sessionCookie(start);
        Map<String, String> query = queryOf(start.getHeaders().getLocation());

        URI callback = login(query).getHeaders().getLocation();
        ResponseEntity<Void> finished = get(callback.getRawPath() + "?" + callback.getRawQuery(), before);

        assertThat(finished.getStatusCode().value()).isEqualTo(302);
        assertThat(finished.getHeaders().getLocation().toString()).endsWith("/page/roles?realm=PORTAL");
        String after = sessionCookie(finished);
        assertThat(after).isNotEqualTo(before);

        assertThat(get("/page/roles?realm=PORTAL", after).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("state 가 다르면 교환하지 않고, 그 세션으로는 화면이 열리지 않는다")
    void rejectsForeignState() {
        ResponseEntity<Void> start = get("/page/roles", null);
        String session = sessionCookie(start);
        Map<String, String> query = queryOf(start.getHeaders().getLocation());

        URI callback = login(query).getHeaders().getLocation();
        String code = queryOf(callback).get("code");
        ResponseEntity<Void> finished = get("/page/login/callback?code=" + code + "&state=someone-else", session);

        assertThat(finished.getStatusCode().value()).isEqualTo(400);
        assertThat(get("/page/roles", session).getStatusCode().value()).isEqualTo(302);
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

    private ResponseEntity<Void> get(String path, String sessionCookie) {
        var request = http.get().uri(URI.create("http://localhost:" + port + path));
        if (sessionCookie != null) {
            request = request.header(HttpHeaders.COOKIE, sessionCookie);
        }
        return request.retrieve().toBodilessEntity();
    }

    private static String sessionCookie(ResponseEntity<Void> response) {
        return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("세션 쿠키가 없다"));
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
