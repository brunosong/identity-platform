package com.brunosong.identityplatform.auth.service.web.admin.console.login;

import com.brunosong.identityplatform.auth.service.application.identity.token.TokenProperties;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * 운영 화면이 쓰는 쿠키 셋. 이름, 경로, 수명을 여기서만 정한다.
 *
 * <table>
 *   <tr><th>쿠키</th><th>담는 것</th><th>경로</th><th>수명</th></tr>
 *   <tr><td>{@code CONSOLE_LOGIN}</td><td>state, PKCE 원본, 돌아갈 주소</td>
 *       <td>콜백 하나</td><td>10분. 콜백에서 바로 지운다</td></tr>
 *   <tr><td>{@code CONSOLE_ACCESS}</td><td>access 토큰</td><td>{@code /}</td><td>access 수명</td></tr>
 *   <tr><td>{@code CONSOLE_REFRESH}</td><td>refresh 토큰</td><td>{@code /}</td><td>refresh 수명</td></tr>
 * </table>
 *
 * <p>토큰 쿠키의 경로가 {@code /} 인 것은 로그인이 지키는 범위가 8080 전체라서다
 * ({@link ConsoleWebConfiguration}). {@code /api/**} 에도 실려 가지만 그쪽은 Bearer 헤더만 읽는다.
 *
 * <p>속성은 모두 같다.
 * <ul>
 *   <li>{@code HttpOnly}: 스크립트가 토큰을 읽지 못하게 한다</li>
 *   <li>{@code SameSite=Lax}: 다른 사이트에서 오는 POST 에는 실리지 않는다. 로그인 서버에서
 *       콜백으로 돌아오는 최상위 이동에는 실려야 해서 {@code Strict} 는 쓰지 않는다</li>
 *   <li>{@code Secure} 는 요청이 https 일 때만. http 로 띄운 로컬에서 켜면 브라우저가 저장하지 않는다</li>
 * </ul>
 *
 * <p>지울 때도 같은 이름과 경로로 굽는다. 다르면 브라우저는 지우는 대신 새 쿠키를 하나 더 만든다.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
class ConsoleCookies {

    private static final String LOGIN = "CONSOLE_LOGIN";
    private static final String ACCESS = "CONSOLE_ACCESS";
    private static final String REFRESH = "CONSOLE_REFRESH";

    private static final String LOGIN_PATH = ConsoleLogin.CALLBACK_PATH;
    private static final String TOKEN_PATH = "/";

    /** 로그인 화면에 머무를 수 있는 시간. 이 안에 돌아오지 않으면 처음부터 다시 한다. */
    private static final Duration LOGIN_TTL = Duration.ofMinutes(10);

    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final TokenProperties tokenProperties;

    /** 로그인 도중에 들고 다니는 값. */
    record LoginInProgress(String state, String verifier, String returnTo) {
    }

    void putLogin(HttpServletRequest request, HttpServletResponse response, LoginInProgress login) {
        // state 와 verifier 는 base64url 이라 ':' 가 들어가지 않는다. 돌아갈 주소는 '?' '&' 를 품으므로 한 번 감싼다.
        String value = login.state() + ":" + login.verifier() + ":"
                + BASE64.encodeToString(login.returnTo().getBytes(StandardCharsets.UTF_8));
        add(request, response, LOGIN, value, LOGIN_PATH, LOGIN_TTL);
    }

    /** 로그인 도중의 값을 꺼낸다. 한 번만 쓰는 값이라 꺼내면서 지운다. 모양이 틀렸으면 없는 것으로 친다. */
    Optional<LoginInProgress> takeLogin(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> value = read(request, LOGIN);
        add(request, response, LOGIN, "", LOGIN_PATH, Duration.ZERO);

        return value.map(v -> v.split(":", -1))
                .filter(parts -> parts.length == 3)
                .map(parts -> new LoginInProgress(parts[0], parts[1],
                        new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8)));
    }

    void putTokens(HttpServletRequest request, HttpServletResponse response, TokenPair tokens) {
        add(request, response, ACCESS, tokens.accessToken(), TOKEN_PATH,
                Duration.ofMillis(tokenProperties.getAccessExpiration()));
        add(request, response, REFRESH, tokens.refreshToken(), TOKEN_PATH,
                Duration.ofMillis(tokenProperties.getRefreshExpiration()));
    }

    void clearTokens(HttpServletRequest request, HttpServletResponse response) {
        add(request, response, ACCESS, "", TOKEN_PATH, Duration.ZERO);
        add(request, response, REFRESH, "", TOKEN_PATH, Duration.ZERO);
    }

    Optional<String> accessToken(HttpServletRequest request) {
        return read(request, ACCESS);
    }

    Optional<String> refreshToken(HttpServletRequest request) {
        return read(request, REFRESH);
    }

    private static void add(HttpServletRequest request, HttpServletResponse response, String name, String value,
                            String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .path(path)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }
}
