package com.brunosong.identityplatform.auth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialIdentityVerifierPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.VerifiedSocialIdentity;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 로그인 화면의 구글 버튼이 <b>code 흐름으로 이어지는지</b> 본다.
 *
 * <p>구글 자체는 부를 수 없으니 구글에 신원을 묻는 어댑터 하나만 대역으로 바꾼다. 나머지는
 * 진짜다. 브라우저가 하는 일(쿠키를 들고 다음 주소로 가기)은 테스트가 손으로 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // 로컬 비밀 파일이 없어도 소셜이 켜진 채로 뜨게 한다. 구글에 실제로 가지는 않는다.
        "social.google.client-id=test-client.apps.googleusercontent.com",
        "social.google.client-secret=test-secret"
})
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class GoogleBrokerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** V9002 시드의 포털 앱과 주소. */
    private static final String CLIENT_ID = "portal-17kqqi85h2ks";
    private static final String REDIRECT_URI = "http://localhost:5173/login/callback";

    /** RFC 7636 부록 B 의 예시 한 쌍. */
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @LocalServerPort
    private int port;

    @MockitoBean
    private SocialIdentityVerifierPort verifier;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    @DisplayName("구글에 다녀오면 원래 인가 요청으로 되돌아가 code 가 나오고, 그 code 로 id_token 까지 받는다")
    void googleLoginEndsInOurCode() {
        String email = "google-" + UUID.randomUUID() + "@example.com";
        given(verifier.verify(any(), eq("google-code")))
                .willReturn(new VerifiedSocialIdentity("uid-" + UUID.randomUUID(), email, "구글 사용자"));

        // 1. 로그인 화면의 구글 버튼. 구글로 보내면서 쿠키 둘을 심는다.
        ResponseEntity<Void> toGoogle = get("/realms/portal/broker/google/login?" + authorizationQuery("app-state"), null);
        assertThat(toGoogle.getStatusCode().value()).isEqualTo(302);
        URI google = toGoogle.getHeaders().getLocation();
        assertThat(google.getHost()).isEqualTo("accounts.google.com");
        String googleState = UriComponentsBuilder.fromUri(google).build().getQueryParams().getFirst("state");
        String cookies = cookieHeader(toGoogle, "SOCIAL_STATE", "SOCIAL_RESUME");

        // 2. 구글이 돌려보낸다. 세션을 심고 원래 인가 요청으로 되돌린다.
        ResponseEntity<Void> back = get("/realms/portal/broker/google/endpoint?code=google-code&state=" + googleState, cookies);
        assertThat(back.getStatusCode().value()).isEqualTo(302);
        String resume = back.getHeaders().getLocation().toString();
        assertThat(resume).contains("/realms/portal/auth?").contains("client_id=" + CLIENT_ID);
        String session = cookieHeader(back, "AUTH_SESSION");

        // 3. 세션이 있으니 인가 요청의 입구가 화면 없이 code 를 내준다. state 는 앱이 보낸 그대로다.
        ResponseEntity<Void> toApp = get(pathOf(resume), session);
        assertThat(toApp.getStatusCode().value()).isEqualTo(303);
        URI app = toApp.getHeaders().getLocation();
        assertThat(app.toString()).startsWith(REDIRECT_URI);
        assertThat(app.getQuery()).contains("state=app-state");
        String code = app.getQuery().replaceAll(".*code=([^&]+).*", "$1");

        // 4. 앱이 code 를 바꾼다. nonce 가 구글을 다녀와도 그대로 실려 있어야 한다.
        ResponseEntity<String> tokens = http.post().uri("/realms/portal/token")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body("grant_type=authorization_code&code=" + code + "&client_id=" + CLIENT_ID
                        + "&redirect_uri=" + REDIRECT_URI + "&code_verifier=" + VERIFIER)
                .retrieve().toEntity(String.class);
        assertThat(tokens.getStatusCode().value()).isEqualTo(200);
        assertThat(payloadOf(fieldOf(tokens.getBody(), "id_token"))).contains("\"nonce\":\"app-nonce\"");
    }

    @Test
    @DisplayName("구글에서 취소하면 세션 없이 되돌아가 로그인 화면이 다시 뜬다")
    void cancelAtGoogleReturnsToLoginScreen() {
        ResponseEntity<Void> toGoogle = get("/realms/portal/broker/google/login?" + authorizationQuery("s"), null);
        String googleState = UriComponentsBuilder.fromUri(toGoogle.getHeaders().getLocation()).build()
                .getQueryParams().getFirst("state");

        ResponseEntity<Void> back = get("/realms/portal/broker/google/endpoint?error=access_denied&state=" + googleState,
                cookieHeader(toGoogle, "SOCIAL_STATE", "SOCIAL_RESUME"));

        assertThat(back.getStatusCode().value()).isEqualTo(302);
        assertThat(back.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("AUTH_SESSION="));
        assertThat(get(pathOf(back.getHeaders().getLocation().toString()), null).getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("이 브라우저가 시작하지 않은 콜백은 받지 않는다")
    void callbackWithoutOurCookiesIsRefused() {
        // 로그인 CSRF. 공격자가 자기 구글 계정으로 받은 콜백 주소를 피해자가 열어도 쿠키 짝이 없다.
        ResponseEntity<Void> back = get("/realms/portal/broker/google/endpoint?code=google-code&state=forged", null);

        assertThat(back.getStatusCode().value()).isEqualTo(400);
        assertThat(back.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("AUTH_SESSION="));
    }

    @Test
    @DisplayName("등록되지 않은 앱의 요청이면 구글에 보내지 않는다")
    void unregisteredClientIsNotSentToGoogle() {
        ResponseEntity<Void> response = get("/realms/portal/broker/google/login?client_id=unknown"
                + "&redirect_uri=" + REDIRECT_URI + "&code_challenge=" + CHALLENGE + "&code_challenge_method=S256", null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getLocation()).isNull();
    }

    @Test
    @DisplayName("직원 realm 에는 구글 로그인이 없다")
    void adminRealmHasNoGoogle() {
        assertThat(get("/realms/admin/broker/google/login?" + authorizationQuery("s"), null)
                .getStatusCode().value()).isEqualTo(404);
    }

    private static String authorizationQuery(String state) {
        return "client_id=" + CLIENT_ID + "&redirect_uri=" + REDIRECT_URI + "&scope=openid"
                + "&state=" + state + "&nonce=app-nonce"
                + "&code_challenge=" + CHALLENGE + "&code_challenge_method=S256";
    }

    private ResponseEntity<Void> get(String uri, String cookies) {
        RestClient.RequestHeadersSpec<?> request = http.get().uri(URI.create("http://localhost:" + port + uri));
        if (cookies != null) {
            request = request.header(HttpHeaders.COOKIE, cookies);
        }
        return request.retrieve().toBodilessEntity();
    }

    /** 되돌아갈 주소는 상대 경로로 올 수도 있다. 경로와 질의만 쓴다. */
    private static String pathOf(String location) {
        URI uri = URI.create(location);
        return uri.getRawPath() + "?" + uri.getRawQuery();
    }

    /** Set-Cookie 에서 이름이 맞는 것만 골라 다음 요청의 Cookie 헤더로 만든다. */
    private static String cookieHeader(ResponseEntity<?> response, String... names) {
        List<String> setCookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        StringBuilder header = new StringBuilder();
        for (String name : names) {
            String value = setCookies.stream()
                    .filter(cookie -> cookie.startsWith(name + "="))
                    .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(name + " 쿠키가 없다: " + setCookies));
            if (!header.isEmpty()) header.append("; ");
            header.append(value);
        }
        return header.toString();
    }

    private static String fieldOf(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(String.valueOf(body));
        if (!matcher.find()) {
            throw new IllegalStateException("본문에 " + field + " 이 없다: " + body);
        }
        return matcher.group(1);
    }

    private static String payloadOf(String jwt) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    }
}
