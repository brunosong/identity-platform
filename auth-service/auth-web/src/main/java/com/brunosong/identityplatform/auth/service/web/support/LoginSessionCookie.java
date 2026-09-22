package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.domain.identity.LoginSession;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.time.Instant;

/**
 * 로그인 세션을 브라우저에 남기는 쿠키.
 *
 * <p>통합 로그인은 이 쿠키가 다음 인가 요청에 실려 오는 것으로 성립한다. 그래서 속성 하나하나가
 * 그 조건이다.
 *
 * <ul>
 *   <li><b>{@code HttpOnly}</b> - 스크립트가 읽을 이유가 없다. 앱은 이 값을 보지 못해야 한다.
 *       앱이 받는 것은 코드와 토큰이고, 세션은 브라우저와 우리 사이의 값이다.</li>
 *   <li><b>{@code SameSite=Lax}</b> - 다른 앱에서 우리 쪽으로 넘어오는 최상위 이동에 실려야 한다.
 *       {@code Strict} 면 그 이동에 쿠키가 빠져서 매번 로그인 화면이 뜬다. 즉 SSO 가 죽는다.
 *       {@code None} 은 남의 사이트에 박힌 프레임에도 실려 나가므로 쓰지 않는다.</li>
 *   <li><b>경로가 {@code /realms/{realm}}</b> - 이 realm 의 요청에만 실린다. realm 마다 쿠키가
 *       저절로 갈리고, {@code /api/**} 같은 다른 요청에는 붙지 않는다. Keycloak 도 같은 방식이다.</li>
 *   <li><b>{@code Secure} 는 요청이 https 일 때만</b> - http 로 띄운 로컬에서 켜면 브라우저가
 *       쿠키를 아예 저장하지 않는다. 기존 {@code SOCIAL_STATE} 쿠키와 같은 규칙이다.</li>
 * </ul>
 *
 * <p>수명을 쿠키에도 적는다. 서버 기록과 같은 시각을 가리키므로, 세션이 끝난 뒤에 브라우저가
 * 쓸모없는 값을 들고 다니지 않는다.
 */
public final class LoginSessionCookie {

    public static final String NAME = "AUTH_SESSION";

    private LoginSessionCookie() {
    }

    public static ResponseCookie of(LoginSession session, boolean secure, Instant now) {
        return base(session.getSessionId(), session.getRealm(), secure)
                .maxAge(session.remaining(now))
                .build();
    }

    /**
     * 지우는 쿠키. 로그아웃이 응답에 싣는다.
     *
     * <p>값을 비우고 수명을 0 으로 준다. <b>속성이 심을 때와 같아야 한다</b> - 브라우저는 이름과
     * 경로가 같아야 같은 쿠키로 알아보고, 다르면 지우는 대신 새 쿠키를 하나 더 만든다.
     * 그러면 지운 줄 알았는데 원래 것이 그대로 실려 다닌다.
     */
    public static ResponseCookie expired(Realm realm, boolean secure) {
        return base("", realm, secure).maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value, Realm realm, boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path(pathFor(realm))
                .sameSite("Lax");
    }

    /**
     * 경로의 realm 은 소문자로 적는다. 브라우저는 쿠키 경로를 글자 그대로 대조하므로,
     * {@code /realms/PORTAL} 로 심어두면 {@code /realms/portal} 요청에 실리지 않는다.
     * 주소는 소문자로 쓰는 것이 이 서비스의 관례다.
     */
    private static String pathFor(Realm realm) {
        return "/realms/" + realm.name().toLowerCase();
    }
}
