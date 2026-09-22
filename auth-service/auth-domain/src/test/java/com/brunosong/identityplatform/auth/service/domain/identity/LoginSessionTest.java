package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 세션의 수명을 고정한다.
 *
 * <p>이 값을 쥔 쪽은 로그인 화면을 거치지 않고 코드를 받아간다. 그래서 추측할 수 없어야 하고,
 * 영원히 살아서도 안 된다.
 */
class LoginSessionTest {

    private static final Instant NOW = Instant.parse("2026-09-22T09:00:00Z");

    private static LoginSession started() {
        return LoginSession.start(Realm.PORTAL, new PrincipalId("p-1"), NOW);
    }

    @Test
    @DisplayName("시작하면 8시간 뒤에 끝나는 세션이 된다")
    void startsWithEightHours() {
        LoginSession session = started();

        assertThat(session.getRealm()).isEqualTo(Realm.PORTAL);
        assertThat(session.getPrincipalId().value()).isEqualTo("p-1");
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));
    }

    @Test
    @DisplayName("세션 값은 추측할 수 없는 난수다")
    void sessionIdIsRandom() {
        assertThat(started().getSessionId())
                .isNotBlank()
                .isNotEqualTo(started().getSessionId())
                .hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("8시간이 지나면 죽는다")
    void expiresAfterEightHours() {
        LoginSession session = started();

        assertThat(session.isExpired(NOW.plus(Duration.ofHours(7).plusMinutes(59)))).isFalse();
        assertThat(session.isExpired(NOW.plus(Duration.ofHours(8)))).isTrue();
    }

    @Test
    @DisplayName("남은 시간은 쿠키에 적을 값이다")
    void remainingIsCookieMaxAge() {
        LoginSession session = started();

        assertThat(session.remaining(NOW.plus(Duration.ofHours(3)))).isEqualTo(Duration.ofHours(5));
        // 이미 지난 세션은 음수가 아니라 0 이다. 쿠키 max-age 에 음수를 적으면 뜻이 달라진다.
        assertThat(session.remaining(NOW.plus(Duration.ofHours(9)))).isEqualTo(Duration.ZERO);
    }
}
