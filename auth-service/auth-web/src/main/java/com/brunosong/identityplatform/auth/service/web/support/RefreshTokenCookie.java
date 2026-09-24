package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Locale;

/**
 * refresh 토큰을 브라우저에 남기는 쿠키.
 *
 * <h2>왜 이것만 쿠키인가</h2>
 * access 토큰은 쿠키로 옮길 수 없다. 가는 곳이 customer-service, order-service 처럼 <b>다른
 * 도메인</b>이고, 우리 도메인에 구운 쿠키는 거기 실리지 않는다. 그래서 access 는 본문으로 내리고
 * 앱이 {@code Authorization} 헤더에 싣는다({@link IssuedTokens}).
 *
 * <p>refresh 토큰은 사정이 다르다. <b>갈 곳이 재발급 엔드포인트 하나뿐이고 그곳은 우리 자신이다.</b>
 * 그래서 쿠키로 옮길 수 있고, 옮기면 스크립트가 읽지 못하는 자리에 놓인다.
 *
 * <p>막는 것은 <b>반출</b>이다. XSS 가 나도 공격자가 이 값을 자기 서버로 가져가지 못한다.
 * 피해자 브라우저 안에서 재발급을 부르는 것까지는 못 막는다 - 쿠키는 브라우저가 알아서 붙이기
 * 때문이다. 오래 사는 자격증명이 밖으로 나가지 않는다는 것만으로도 값을 한다.
 *
 * <h2>속성</h2>
 * <ul>
 *   <li><b>{@code HttpOnly}</b> - 이것이 목적이다. 스크립트가 읽으면 본문으로 주는 것과 같다.</li>
 *   <li><b>경로가 {@code /api/auth/realms/{realm}/token}</b> - 재발급 요청에만 실린다.
 *       좁히지 않으면 JWKS 조회, 권한 조회 같은 모든 auth 호출에 1KB 가 따라붙는다.
 *       realm 이 경로에 있으므로 realm 마다 쿠키가 저절로 갈린다.</li>
 *   <li><b>{@code SameSite=Lax}</b> - 로컬에서 앱(5173)과 auth(8080)는 포트만 다르고
 *       <b>같은 사이트</b>다(사이트는 포트를 따지지 않는다). 그래서 스크립트가 보내는 요청에도
 *       실린다. 운영에서 등록가능 도메인이 갈리면({@code portal.co.kr} 과 {@code auth.example.com})
 *       {@code SameSite=None; Secure} 가 필요해지고, 그때는 서드파티 쿠키 정책에 걸린다.</li>
 *   <li><b>{@code Secure} 는 요청이 https 일 때만</b> - http 로 띄운 로컬에서 켜면 브라우저가
 *       쿠키를 아예 저장하지 않는다. {@link LoginSessionCookie} 와 같은 규칙이다.</li>
 * </ul>
 *
 * <p>수명은 refresh 토큰 자체의 수명과 맞춘다. 쿠키가 더 오래 남으면 브라우저가 이미 죽은 값을
 * 계속 들고 다닌다.
 */
public final class RefreshTokenCookie {

    public static final String NAME = "REFRESH_TOKEN";

    private RefreshTokenCookie() {
    }

    public static ResponseCookie of(String refreshToken, Realm realm, boolean secure,
                                    Duration lifetime) {
        return base(refreshToken, realm, secure).maxAge(lifetime).build();
    }

    /**
     * 지우는 쿠키. 값을 비우고 수명을 0 으로 준다.
     *
     * <p><b>속성이 심을 때와 같아야 한다.</b> 브라우저는 이름과 경로가 같아야 같은 쿠키로 알아보고,
     * 다르면 지우는 대신 새 쿠키를 하나 더 만든다.
     */
    public static ResponseCookie expired(Realm realm, boolean secure) {
        return base("", realm, secure).maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value, Realm realm,
                                                             boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path(pathFor(realm))
                .sameSite("Lax");
    }

    /**
     * 경로의 realm 은 소문자로 적는다. 브라우저는 쿠키 경로를 글자 그대로 대조하므로,
     * {@code /realms/PORTAL} 로 심어두면 소문자 요청에 실리지 않는다.
     */
    private static String pathFor(Realm realm) {
        return "/api/auth/realms/" + realm.name().toLowerCase(Locale.ROOT) + "/token";
    }
}
