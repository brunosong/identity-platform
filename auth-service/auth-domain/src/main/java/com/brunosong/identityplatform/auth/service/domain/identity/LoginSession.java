package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 로그인 세션 - "이 브라우저에는 이 사람이 로그인해 있다".
 *
 * <p>통합 로그인이 여기서 나온다. 앱마다 로그인하지 않아도 되는 이유가 이 값 하나다. 두 번째 앱이
 * 인가 요청을 보내면 우리는 쿠키에서 이 세션을 찾고, 살아 있으면 화면을 띄우지 않고 바로 코드를
 * 내준다. 사람은 앱을 옮겼을 뿐인데 로그인은 한 번만 한 것이 된다.
 *
 * <h2>토큰과 무엇이 다른가</h2>
 * 토큰은 <b>앱이</b> 들고 다니며 API 를 부를 때 쓴다. 이 세션은 <b>브라우저가</b> 들고 auth 에만
 * 보낸다(쿠키 경로가 {@code /realms/{realm}} 로 좁혀져 있다). 앱은 이 값을 보지도 못한다.
 * 그래서 앱에서 로그아웃해 토큰을 버려도 이 세션은 남고, 그 반대도 마찬가지다.
 *
 * <h2>수명은 절대 만료 하나다</h2>
 * 만든 지 8시간이 지나면 끝난다. 쓸 때마다 연장하는 방식(유휴 만료)은 세션을 들여다볼 때마다
 * 쓰기가 생기고, 그만큼 "언제 끝나는가" 를 코드 여러 곳이 정하게 된다. 지금은 한 자리에서 정한다.
 */
@Getter
public class LoginSession {

    /** 근무 하루를 덮는 길이. 아침에 로그인하면 퇴근까지 간다. */
    private static final Duration LIFETIME = Duration.ofHours(8);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 쿠키에 실려 브라우저에 남는 값. 이것을 쥔 쪽이 그 사람으로 통하므로 난수다. */
    private final String sessionId;

    /** 이 세션이 속한 realm. 쿠키 경로도 realm 으로 갈려 있어 고객 세션으로 어드민에 들어갈 수 없다. */
    private final Realm realm;

    private final PrincipalId principalId;
    private final Instant createdAt;
    private final Instant expiresAt;

    private LoginSession(String sessionId, Realm realm, PrincipalId principalId,
                         Instant createdAt, Instant expiresAt) {
        this.sessionId = sessionId;
        this.realm = realm;
        this.principalId = principalId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** 로그인 화면에서 자격증명이 확인된 직후에 시작한다. */
    public static LoginSession start(Realm realm, PrincipalId principalId, Instant now) {
        return new LoginSession(randomId(), realm, principalId, now, now.plus(LIFETIME));
    }

    public static LoginSession restore(String sessionId, Realm realm, PrincipalId principalId,
                                       Instant createdAt, Instant expiresAt) {
        return new LoginSession(sessionId, realm, principalId, createdAt, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** 쿠키에 남길 시간. 만료 시각을 쿠키에도 적어 두면 브라우저가 알아서 버린다. */
    public Duration remaining(Instant now) {
        Duration left = Duration.between(now, expiresAt);
        return left.isNegative() ? Duration.ZERO : left;
    }

    private static String randomId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
