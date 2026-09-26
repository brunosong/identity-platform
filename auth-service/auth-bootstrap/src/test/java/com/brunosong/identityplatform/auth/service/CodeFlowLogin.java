package com.brunosong.identityplatform.auth.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 테스트가 토큰을 받는 길. 앱이 하는 그대로 로그인 화면의 폼을 내고, 받은 code 를 바꾼다.
 *
 * <p>토큰을 바로 내주는 로그인 API 는 없다. 토큰은 code 교환에서만 나간다. 그래서 토큰이 필요한
 * 테스트는 이 걸음을 밟는다. 화면을 그리지는 않고 폼이 보내는 값만 보낸다.
 */
final class CodeFlowLogin {

    /** RFC 7636 부록 B 의 예시 한 쌍. */
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    /** V9002 시드의 앱들. */
    private static final App PORTAL = new App("portal", "portal-17kqqi85h2ks", "http://localhost:5173/login/callback");
    private static final App ADMIN = new App("admin", "admin-ln4efwmg0tee", "http://localhost:5174/login/callback");

    private final RestClient http;

    CodeFlowLogin(int port) {
        this.http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    /** 포털 고객으로 비밀번호 로그인하고 토큰 응답 본문을 돌려준다. */
    Tokens portalWithPassword(String loginId, String password) {
        ResponseEntity<Void> response = form("/realms/portal/auth/login", PORTAL,
                "&loginId=" + encode(loginId) + "&password=" + encode(password));
        return exchange(PORTAL, codeFrom(response));
    }

    /**
     * 직원으로 인증번호 로그인한다. 직원은 비밀번호 계정이 없다. local 프로파일은 메일을 보내지 않고
     * 고정코드 123456 을 쓴다.
     *
     * <p>인증번호에는 재발송 쿨다운이 있다. 짧은 간격으로 두 번 부르면 두 번째 발송이 조용히
     * 무시되고 첫 번호는 이미 쓰였으므로 로그인이 실패한다. 부르는 쪽이 한 번만 부르고 재사용한다.
     */
    Tokens adminWithOtp(String email) {
        form("/realms/admin/auth/send-code", ADMIN, "&email=" + encode(email));
        ResponseEntity<Void> response = form("/realms/admin/auth/login/otp", ADMIN,
                "&email=" + encode(email) + "&code=123456");
        return exchange(ADMIN, codeFrom(response));
    }

    private ResponseEntity<Void> form(String path, App app, String extra) {
        return http.post().uri(path)
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body("client_id=" + app.clientId()
                        + "&redirect_uri=" + encode(app.redirectUri())
                        + "&code_challenge=" + CHALLENGE
                        + "&code_challenge_method=S256"
                        + extra)
                .retrieve().toBodilessEntity();
    }

    private Tokens exchange(App app, String code) {
        String body = http.post().uri("/realms/" + app.realm() + "/token")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body("grant_type=authorization_code&code=" + code
                        + "&client_id=" + app.clientId()
                        + "&redirect_uri=" + encode(app.redirectUri())
                        + "&code_verifier=" + VERIFIER)
                .retrieve().body(String.class);
        return new Tokens(fieldOf(body, "access_token"), fieldOf(body, "refresh_token"));
    }

    private static String codeFrom(ResponseEntity<Void> response) {
        URI location = response.getHeaders().getLocation();
        if (response.getStatusCode().value() != 303 || location == null || location.getQuery() == null
                || !location.getQuery().contains("code=")) {
            throw new IllegalStateException("로그인이 code 로 끝나지 않았다: " + response.getStatusCode());
        }
        return location.getQuery().replaceAll(".*code=([^&]+).*", "$1");
    }

    private static String fieldOf(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(String.valueOf(body));
        if (!matcher.find()) {
            throw new IllegalStateException("토큰 응답에 " + field + " 이 없다: " + body);
        }
        return matcher.group(1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record Tokens(String accessToken, String refreshToken) {
    }

    private record App(String realm, String clientId, String redirectUri) {
    }
}
