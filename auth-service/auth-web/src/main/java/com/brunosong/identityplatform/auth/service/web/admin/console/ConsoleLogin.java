package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RevokeRefreshTokenUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.token.TokenProperties;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.ExchangeAuthorizationCodeUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.ExchangeAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AccessTokenReader;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * 운영 화면이 MASTER realm 에 로그인하는 방법. Keycloak 콘솔이 master 의 앱 하나로 로그인하는 것과 같다.
 *
 * <p>운영 화면도 {@code auth-console} 이라는 앱일 뿐이다. 다른 앱과 똑같이 브라우저를
 * {@code /realms/master/auth} 로 보내고, 돌아온 code 를 토큰으로 바꾼다. code 교환은 HTTP 로 하지 않고
 * 유스케이스를 바로 부른다. 같은 프로세스라 자기 자신에게 요청을 보낼 이유가 없다. PKCE, 1회용,
 * 돌아갈 주소 대조는 똑같이 거친다.
 *
 * <h2>서버는 아무것도 기억하지 않는다</h2>
 * 로그인 도중의 값(state, PKCE 원본)도, 받은 토큰도 전부 {@code HttpOnly} 쿠키에 둔다. 서버 세션에
 * 두면 auth 를 여러 대 띄웠을 때 시작한 인스턴스와 돌아온 인스턴스가 달라 깨진다. 쿠키에 두면 어느
 * 인스턴스든 쿠키의 토큰을 자기 공개키로 검증하면 된다.
 *
 * <p>대가는 토큰 원본이 브라우저에 있다는 것이다. 스크립트는 못 읽지만({@code HttpOnly}), 쿠키가
 * 털리면 토큰도 털린다. 로그아웃해도 이미 나간 access 는 만료까지 산다.
 *
 * <p>쿠키의 토큰이 있다고 믿지 않는다. 요청마다 서명, 발급자, 만료를 다시 본다. 관리 API 가 헤더의
 * 토큰을 검증하는 것과 같은 검증기다.
 */
@Component
@Profile("local")
public class ConsoleLogin {

    /** V9003 시드의 앱. Keycloak 의 security-admin-console 자리다. */
    static final String CLIENT_ID = "auth-console";
    static final String CALLBACK_PATH = "/page/login/callback";
    static final String LOGOUT_PATH = "/page/logout";

    /** 로그인 도중의 값. 콜백 한 곳에서만 읽으므로 경로를 거기로 좁힌다. */
    static final String LOGIN_COOKIE = "CONSOLE_LOGIN";
    static final String ACCESS_COOKIE = "CONSOLE_ACCESS";
    static final String REFRESH_COOKIE = "CONSOLE_REFRESH";

    /**
     * 토큰 쿠키의 경로. 로그인이 지키는 범위가 8080 전체라 쿠키도 전체에 실린다
     * ({@link ConsoleWebConfiguration}). {@code /api/**} 에도 실려 가지만 그쪽은 Bearer 헤더만 읽는다.
     */
    private static final String TOKEN_COOKIE_PATH = "/";
    static final String DEFAULT_PAGE = "/page/oauth-clients";

    /** 로그인 화면에 머무를 수 있는 시간. 이 안에 돌아오지 않으면 처음부터 다시 한다. */
    private static final Duration LOGIN_TTL = Duration.ofMinutes(10);

    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();

    private final AccessTokenReader accessTokenReader;
    private final ExchangeAuthorizationCodeUseCase exchangeAuthorizationCode;
    private final RevokeRefreshTokenUseCase revokeRefreshToken;
    private final TokenProperties tokenProperties;

    public ConsoleLogin(AccessTokenReader accessTokenReader,
                        ExchangeAuthorizationCodeUseCase exchangeAuthorizationCode,
                        RevokeRefreshTokenUseCase revokeRefreshToken,
                        TokenProperties tokenProperties) {
        this.accessTokenReader = accessTokenReader;
        this.exchangeAuthorizationCode = exchangeAuthorizationCode;
        this.revokeRefreshToken = revokeRefreshToken;
        this.tokenProperties = tokenProperties;
    }

    /**
     * 쿠키의 access 토큰이 MASTER 의 유효한 access 토큰인가.
     *
     * <p>{@code type} 까지 본다. refresh 토큰도 같은 키로 서명돼 있어 서명과 발급자만 보면 통과한다.
     * 그러면 24시간짜리 refresh 를 access 자리에 넣어 1분 수명을 건너뛸 수 있다.
     */
    boolean loggedIn(HttpServletRequest request) {
        return cookie(request, ACCESS_COOKIE)
                .flatMap(token -> accessTokenReader.read(Realm.MASTER, token))
                .filter(claims -> "access".equals(claims.get("type", String.class)))
                .isPresent();
    }

    /**
     * 로그인을 시작한다. state 와 PKCE 원본을 쿠키에 적고, 브라우저를 보낼 주소를 돌려준다.
     *
     * <p>GET 이면 로그인 뒤 그 주소로 돌아온다. 폼을 내다가 걸렸으면 되살릴 본문이 없으니 기본 화면으로 간다.
     */
    String start(HttpServletRequest request, HttpServletResponse response) {
        String state = randomValue();
        String verifier = randomValue();
        String returnTo = "GET".equals(request.getMethod()) ? requestedPath(request) : DEFAULT_PAGE;

        // state 와 verifier 는 base64url 이라 ':' 가 들어가지 않는다. 돌아갈 주소는 '?' '&' 를 품으므로 한 번 감싼다.
        String value = state + ":" + verifier + ":" + BASE64.encodeToString(returnTo.getBytes(StandardCharsets.UTF_8));
        addCookie(response, LOGIN_COOKIE, value, CALLBACK_PATH, LOGIN_TTL, request.isSecure());

        return UriComponentsBuilder.fromUriString(tokenProperties.getIssuer())
                .path("/realms/master/auth")
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("redirect_uri", redirectUri())
                .queryParam("state", state)
                .queryParam("code_challenge", challengeOf(verifier))
                .queryParam("code_challenge_method", "S256")
                .encode()
                .build()
                .toUriString();
    }

    /**
     * 돌아온 code 를 토큰으로 바꿔 쿠키에 두고, 돌아갈 화면을 돌려준다.
     *
     * <p>로그인 쿠키는 성공하든 실패하든 지운다. 한 번만 쓰는 값이다.
     *
     * <p>state 가 맞지 않으면 교환하지 않는다. 남이 자기 code 를 이 브라우저에 밀어 넣어 그 사람으로
     * 로그인시키는 길(로그인 CSRF)을 막는 자리다. 다른 사이트는 우리 쿠키를 심을 수 없으니, 쿠키의 state 와
     * 주소의 state 가 같다는 것은 이 브라우저가 시작한 로그인이라는 뜻이다.
     */
    String finish(HttpServletRequest request, HttpServletResponse response, String code, String state) {
        Optional<String> login = cookie(request, LOGIN_COOKIE);
        addCookie(response, LOGIN_COOKIE, "", CALLBACK_PATH, Duration.ZERO, request.isSecure());

        String[] parts = login.map(value -> value.split(":", -1)).orElse(new String[0]);
        if (parts.length != 3) {
            throw new ConsoleLoginException("로그인을 시작한 기록이 없습니다.");
        }
        String expectedState = parts[0];
        String verifier = parts[1];
        String returnTo = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);

        if (!Objects.equals(expectedState, state) || code == null) {
            throw new ConsoleLoginException("로그인을 시작한 요청과 돌아온 응답이 맞지 않습니다.");
        }

        TokenPair tokens = exchangeAuthorizationCode.exchange(new ExchangeAuthorizationCodeCommand(
                Realm.MASTER, code, CLIENT_ID, redirectUri(), verifier, null)).authentication().tokens();

        addCookie(response, ACCESS_COOKIE, tokens.accessToken(), TOKEN_COOKIE_PATH,
                Duration.ofMillis(tokenProperties.getAccessExpiration()), request.isSecure());
        addCookie(response, REFRESH_COOKIE, tokens.refreshToken(), TOKEN_COOKIE_PATH,
                Duration.ofMillis(tokenProperties.getRefreshExpiration()), request.isSecure());

        // 돌아갈 주소는 쿠키에서 왔다. 이 서버 밖으로 내보내는 데 쓰이지 않게 한다. "//" 로 시작하면
        // 브라우저는 다른 호스트로 읽는다.
        return returnTo.startsWith("/") && !returnTo.startsWith("//") ? returnTo : DEFAULT_PAGE;
    }

    /**
     * 운영 화면 쪽 로그아웃. 자기가 쥔 것을 치우고, SSO 세션을 끊을 로그인 서버의 주소를 돌려준다.
     *
     * <p>refresh 계보를 여기서 끊는다. 로그인 서버의 로그아웃은 앱이 든 refresh 토큰을 받지 않는다.
     * 쿠키만 지우면 그 값을 복사해 둔 쪽이 수명 동안 새 access 를 받아 간다.
     *
     * <p>SSO 세션({@code AUTH_SESSION})은 여기서 못 지운다. 그 쿠키는 {@code /realms/master} 요청에만
     * 실린다. 그래서 브라우저를 로그인 서버의 로그아웃으로 보내 거기서 끝내게 한다. 끝나면 루트로
     * 돌아오고, 루트는 토큰이 없으니 로그인 화면으로 간다.
     */
    String logout(HttpServletRequest request, HttpServletResponse response) {
        cookie(request, REFRESH_COOKIE).ifPresent(token -> revokeRefreshToken.revoke(Realm.MASTER, token));
        addCookie(response, ACCESS_COOKIE, "", TOKEN_COOKIE_PATH, Duration.ZERO, request.isSecure());
        addCookie(response, REFRESH_COOKIE, "", TOKEN_COOKIE_PATH, Duration.ZERO, request.isSecure());

        return UriComponentsBuilder.fromUriString(tokenProperties.getIssuer())
                .path("/realms/master/logout")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("post_logout_redirect_uri", tokenProperties.getIssuer() + "/")
                .encode()
                .build()
                .toUriString();
    }

    /** 돌아갈 주소는 설정의 발급자 주소에서 만든다. 두 군데 적으면 포트를 옮길 때 한쪽만 고친다. */
    private String redirectUri() {
        return tokenProperties.getIssuer() + CALLBACK_PATH;
    }

    /**
     * 쿠키는 전부 같은 속성으로 심는다.
     *
     * <ul>
     *   <li>{@code HttpOnly}: 스크립트가 토큰을 읽지 못하게 한다</li>
     *   <li>{@code SameSite=Lax}: 다른 사이트에서 오는 POST 에는 실리지 않는다. 로그인 서버에서
     *       콜백으로 돌아오는 최상위 이동에는 실려야 해서 {@code Strict} 는 쓰지 않는다</li>
     *   <li>{@code Secure} 는 요청이 https 일 때만. http 로 띄운 로컬에서 켜면 브라우저가 저장하지 않는다</li>
     * </ul>
     */
    private static void addCookie(HttpServletResponse response, String name, String value, String path,
                                  Duration maxAge, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static Optional<String> cookie(HttpServletRequest request, String name) {
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

    private static String requestedPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return BASE64.encodeToString(bytes);
    }

    private static String challengeOf(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return BASE64.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 운영 화면 로그인을 끝낼 수 없을 때. 사유를 화면에 그대로 보여 준다. */
    static class ConsoleLoginException extends RuntimeException {
        ConsoleLoginException(String message) {
            super(message);
        }
    }
}
