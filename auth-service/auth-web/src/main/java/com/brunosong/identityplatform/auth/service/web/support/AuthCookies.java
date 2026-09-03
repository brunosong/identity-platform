package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * 토큰 쿠키의 발급·판독·제거를 한곳에 모은다.
 *
 * <p>auth 가 토큰 전송(쿠키)까지 책임진다. 그래서 호스트는 토큰을 만들지도 담지도 않고, 로그인 화면과
 * 게이트웨이 검증만 남는다. 로그인·재발급·로그아웃이 모두 같은 쿠키 규칙을 써야 하므로 규칙을 이 한
 * 클래스에 둔다 — 컨트롤러마다 굽던 시절에는 이름·만료·SameSite 가 조금씩 어긋났다.
 *
 * <p>만료는 토큰 TTL 에서 끌어온다. 쿠키 수명을 따로 상수로 두면 토큰은 살아 있는데 쿠키가 먼저
 * 사라지거나(그 반대이거나) 하는 어긋남이 생긴다.
 */
@Component
public class AuthCookies {

    /** 쿠키를 토큰보다 아주 조금 길게 잡는다 — 경계 시각에 쿠키가 먼저 사라져 401 대신 비로그인이 되는 것을 막는다. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(10);

    private final boolean secure;
    private final String accessCookieName;
    private final String refreshCookieName;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public AuthCookies(
            @Value("${app.cookie.secure:false}") boolean secure,
            // 호스트별 쿠키 이름 — 같은 도메인을 공유하는 호스트끼리 쿠키가 충돌하지 않게 분리한다.
            @Value("${app.cookie.access-name:accessToken}") String accessCookieName,
            @Value("${app.cookie.refresh-name:refreshToken}") String refreshCookieName,
            @Value("${token.accessExpiration:7200000}") long accessExpirationMillis,
            @Value("${token.refreshExpiration:86400000}") long refreshExpirationMillis) {
        this.secure = secure;
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
        this.accessTtl = Duration.ofMillis(accessExpirationMillis);
        this.refreshTtl = Duration.ofMillis(refreshExpirationMillis);
    }

    public String accessCookieName() {
        return accessCookieName;
    }

    /** 발급된 토큰쌍을 쿠키로 싣는다. refreshToken 이 없으면 access 만 싣는다. */
    public void write(HttpServletResponse response, TokenPair tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(accessCookieName, tokens.accessToken(), accessTtl));
        if (StringUtils.hasText(tokens.refreshToken())) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshCookieName, tokens.refreshToken(), refreshTtl));
        }
    }

    /** 두 쿠키를 즉시 만료시킨다(로그아웃). */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(accessCookieName, "", Duration.ZERO));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshCookieName, "", Duration.ZERO));
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return readCookie(request, accessCookieName);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, refreshCookieName);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private String cookie(String name, String value, Duration ttl) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(ttl.isZero() ? Duration.ZERO : ttl.plus(EXPIRY_MARGIN))
                .sameSite("Strict")
                .build()
                .toString();
    }
}
