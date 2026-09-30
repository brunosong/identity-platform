package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.ExchangeAuthorizationCodeUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.ExchangeAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AccessTokenReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * 운영 화면이 MASTER realm 에 로그인하는 방법. Keycloak 콘솔이 master 의 앱 하나로 로그인하는 것과 같다.
 *
 * <p>운영 화면도 {@code auth-console} 이라는 앱일 뿐이다. 다른 앱과 똑같이 브라우저를
 * {@code /realms/master/auth} 로 보내고, 돌아온 code 를 토큰으로 바꾼다. 다른 점은 둘이다.
 *
 * <ul>
 *   <li>토큰을 브라우저가 아니라 서버 세션에 둔다. 화면을 서버가 그리니 토큰을 쓰는 쪽도 서버다.</li>
 *   <li>code 교환을 HTTP 로 하지 않고 유스케이스를 바로 부른다. 같은 프로세스라 자기 자신에게
 *       요청을 보낼 이유가 없다. PKCE, 1회용, 돌아갈 주소 대조는 똑같이 거친다.</li>
 * </ul>
 *
 * <p>세션에 토큰이 있다고 믿지 않는다. 요청마다 서명과 만료를 다시 본다. 관리 API 가 헤더의 토큰을
 * 검증하는 것과 같은 기준이어야 화면과 API 가 사람을 다르게 믿는 일이 없다.
 */
@Component
@Profile("local")
public class ConsoleLogin {

    /** V9003 시드의 앱. Keycloak 의 security-admin-console 자리다. */
    static final String CLIENT_ID = "auth-console";
    static final String CALLBACK_PATH = "/page/login/callback";

    private static final String DEFAULT_PAGE = "/page/oauth-clients";

    private static final String ACCESS_TOKEN = "console.accessToken";
    private static final String REFRESH_TOKEN = "console.refreshToken";
    private static final String STATE = "console.state";
    private static final String VERIFIER = "console.verifier";
    private static final String RETURN_TO = "console.returnTo";

    private final SecureRandom random = new SecureRandom();

    private final AccessTokenReader accessTokenReader;
    private final ExchangeAuthorizationCodeUseCase exchangeAuthorizationCode;
    private final String issuer;

    public ConsoleLogin(AccessTokenReader accessTokenReader,
                        ExchangeAuthorizationCodeUseCase exchangeAuthorizationCode,
                        @Value("${token.issuer}") String issuer) {
        this.accessTokenReader = accessTokenReader;
        this.exchangeAuthorizationCode = exchangeAuthorizationCode;
        this.issuer = issuer;
    }

    /** 세션의 access 토큰이 MASTER 의 유효한 토큰인가. */
    boolean loggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        return accessTokenReader.read(Realm.MASTER, (String) session.getAttribute(ACCESS_TOKEN)).isPresent();
    }

    /**
     * 로그인을 시작한다. state 와 PKCE 원본을 세션에 적고, 브라우저를 보낼 주소를 돌려준다.
     *
     * <p>GET 이면 로그인 뒤 그 주소로 돌아온다. 폼을 내다가 걸렸으면 되살릴 본문이 없으니 기본 화면으로 간다.
     */
    String start(HttpServletRequest request) {
        String state = randomValue();
        String verifier = randomValue();

        HttpSession session = request.getSession();
        session.setAttribute(STATE, state);
        session.setAttribute(VERIFIER, verifier);
        session.setAttribute(RETURN_TO, "GET".equals(request.getMethod()) ? requestedPath(request) : DEFAULT_PAGE);

        return UriComponentsBuilder.fromUriString(issuer)
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
     * 돌아온 code 를 토큰으로 바꿔 세션에 두고, 돌아갈 화면을 돌려준다.
     *
     * <p>state 는 한 번 쓰고 버린다. 맞지 않으면 교환하지 않는다. 남이 자기 code 를 이 브라우저에
     * 밀어 넣어 그 사람으로 로그인시키는 길(로그인 CSRF)을 막는 자리다.
     *
     * <p>로그인이 성립하면 세션 id 를 바꾼다. 로그인 전에 알려진 id 가 그대로 관리자 세션이 되지 않게 한다.
     */
    String finish(HttpServletRequest request, String code, String state) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ConsoleLoginException("로그인을 시작한 기록이 없습니다.");
        }
        String expectedState = (String) session.getAttribute(STATE);
        String verifier = (String) session.getAttribute(VERIFIER);
        String returnTo = (String) session.getAttribute(RETURN_TO);
        session.removeAttribute(STATE);
        session.removeAttribute(VERIFIER);
        session.removeAttribute(RETURN_TO);

        if (expectedState == null || !Objects.equals(expectedState, state) || code == null) {
            throw new ConsoleLoginException("로그인을 시작한 요청과 돌아온 응답이 맞지 않습니다.");
        }

        TokenPair tokens = exchangeAuthorizationCode.exchange(new ExchangeAuthorizationCodeCommand(
                Realm.MASTER, code, CLIENT_ID, redirectUri(), verifier, null)).authentication().tokens();

        request.changeSessionId();
        session.setAttribute(ACCESS_TOKEN, tokens.accessToken());
        session.setAttribute(REFRESH_TOKEN, tokens.refreshToken());
        return returnTo != null ? returnTo : DEFAULT_PAGE;
    }

    /** 돌아갈 주소는 설정의 발급자 주소에서 만든다. 두 군데 적으면 포트를 옮길 때 한쪽만 고친다. */
    private String redirectUri() {
        return issuer + CALLBACK_PATH;
    }

    private static String requestedPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challengeOf(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
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
