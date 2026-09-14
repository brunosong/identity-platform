package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비밀번호 계정의 brute-force 잠금 규칙을 고정한다.
 *
 * <p>잠금 판정에 현재 시각을 인자로 받는 이유는 도메인이 시계를 직접 읽지 않게 하기 위해서다.
 * 여기서 시각을 고정할 수 있어 만료 경계를 정확히 검증한다.
 *
 * <p>임계 도달 시 카운트를 0 으로 되돌리는 것이 이 모델의 핵심 선택이다 — 잠금이 풀린 뒤에는
 * 새 창에서 다시 세기 시작한다. 되돌리지 않으면 잠금 해제 직후 한 번만 틀려도 다시 잠긴다.
 */
class PasswordAccountTest {

    private static final PrincipalId PRINCIPAL_ID = PrincipalId.newId();
    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(10);

    private static PasswordAccount account() {
        return PasswordAccount.create(PRINCIPAL_ID, Realm.PORTAL, "user01", "hashed");
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("연결할 신원 없이는 만들 수 없다")
        void requiresPrincipal() {
            assertThatThrownBy(() -> PasswordAccount.create(null, Realm.PORTAL, "user01", "hashed"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("principalId");
        }

        @Test
        @DisplayName("로그인ID 가 비어 있으면 만들 수 없다")
        void requiresLoginId() {
            assertThatThrownBy(() -> PasswordAccount.create(PRINCIPAL_ID, Realm.PORTAL, "  ", "hashed"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("loginId");
        }

        @Test
        @DisplayName("비밀번호 해시가 비어 있으면 만들 수 없다")
        void requiresPasswordHash() {
            // 원문을 받지 않는다는 뜻이기도 하다. 해시는 호출자가 만들어 넘긴다.
            assertThatThrownBy(() -> PasswordAccount.create(PRINCIPAL_ID, Realm.PORTAL, "user01", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("passwordHash");
        }

        @Test
        @DisplayName("실패 이력 없이 잠기지 않은 상태로 시작한다")
        void startsClean() {
            PasswordAccount account = account();

            assertThat(account.getFailedAttempts()).isZero();
            assertThat(account.getLockedUntil()).isNull();
            assertThat(account.hasFailureState()).isFalse();
            assertThat(account.isLocked(NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("실패 기록과 잠금")
    class Failure {

        @Test
        @DisplayName("임계 전에는 카운트만 늘고 잠기지 않는다")
        void countsBeforeThreshold() {
            PasswordAccount account = account();

            account.recordFailure(NOW, 5, LOCK);
            account.recordFailure(NOW, 5, LOCK);

            assertThat(account.getFailedAttempts()).isEqualTo(2);
            assertThat(account.isLocked(NOW)).isFalse();
        }

        @Test
        @DisplayName("임계에 도달하면 잠그고 카운트를 되돌린다")
        void locksAtThreshold() {
            PasswordAccount account = account();

            for (int i = 0; i < 5; i++) {
                account.recordFailure(NOW, 5, LOCK);
            }

            assertThat(account.isLocked(NOW)).isTrue();
            assertThat(account.getLockedUntil()).isEqualTo(NOW.plus(LOCK));
            assertThat(account.getFailedAttempts()).isZero();
        }

        @Test
        @DisplayName("잠금 시각이 지나면 다시 풀린다")
        void unlocksAfterDuration() {
            PasswordAccount account = account();
            for (int i = 0; i < 5; i++) {
                account.recordFailure(NOW, 5, LOCK);
            }

            assertThat(account.isLocked(NOW.plus(LOCK).minusSeconds(1))).isTrue();
            assertThat(account.isLocked(NOW.plus(LOCK))).isFalse();
            assertThat(account.isLocked(NOW.plus(LOCK).plusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("잠금이 풀려도 실패 상태는 남아 있다")
        void keepsFailureStateAfterUnlock() {
            // 인증 성공 시 정리 대상인지 판단하는 값이다. lockedUntil 이 남아 있으면 정리한다.
            PasswordAccount account = account();
            for (int i = 0; i < 5; i++) {
                account.recordFailure(NOW, 5, LOCK);
            }

            assertThat(account.isLocked(NOW.plus(LOCK))).isFalse();
            assertThat(account.hasFailureState()).isTrue();
        }

        @Test
        @DisplayName("잠금이 풀린 뒤에는 새 창에서 다시 센다")
        void countsFreshAfterUnlock() {
            PasswordAccount account = account();
            for (int i = 0; i < 5; i++) {
                account.recordFailure(NOW, 5, LOCK);
            }
            Instant later = NOW.plus(LOCK).plusSeconds(1);

            account.recordFailure(later, 5, LOCK);

            assertThat(account.getFailedAttempts()).isEqualTo(1);
            assertThat(account.isLocked(later)).isFalse();
        }
    }

    @Nested
    @DisplayName("성공 후 정리")
    class Reset {

        @Test
        @DisplayName("실패 카운트와 잠금을 모두 지운다")
        void clearsEverything() {
            PasswordAccount account = account();
            for (int i = 0; i < 5; i++) {
                account.recordFailure(NOW, 5, LOCK);
            }

            account.resetFailureState();

            assertThat(account.getFailedAttempts()).isZero();
            assertThat(account.getLockedUntil()).isNull();
            assertThat(account.hasFailureState()).isFalse();
            assertThat(account.isLocked(NOW)).isFalse();
        }

        @Test
        @DisplayName("실패 카운트만 있어도 정리 대상으로 본다")
        void failureStateByCountAlone() {
            PasswordAccount account = account();
            account.recordFailure(NOW, 5, LOCK);

            assertThat(account.hasFailureState()).isTrue();
        }
    }
}
