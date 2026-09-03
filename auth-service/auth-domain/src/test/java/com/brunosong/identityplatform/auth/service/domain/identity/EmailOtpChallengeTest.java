package com.brunosong.identityplatform.auth.service.domain.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이메일 OTP 챌린지의 만료·시도 제한을 고정한다.
 *
 * <p>만료 판정에 현재 시각을 인자로 받아 도메인이 시계를 읽지 않게 한다. 경계(만료 시각 정각)에서
 * 아직 유효한지가 여기서 정해진다.
 *
 * <p>코드 원문은 담지 않는다. 해시만 보관하는 것이 이 애그리거트의 전제다.
 */
class EmailOtpChallengeTest {

    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(300);

    private static EmailOtpChallenge issued() {
        return EmailOtpChallenge.issue("user@example.com", "hashed-code", NOW, EXPIRES_AT);
    }

    @Test
    @DisplayName("발급하면 미사용·시도 0 이고 아직 저장 전이다")
    void startsUnused() {
        EmailOtpChallenge challenge = issued();

        assertThat(challenge.isUsed()).isFalse();
        assertThat(challenge.getAttemptCount()).isZero();
        assertThat(challenge.getId()).isNull();
    }

    @Test
    @DisplayName("코드는 해시로만 들고 있다")
    void keepsHashOnly() {
        assertThat(issued().getCodeHash()).isEqualTo("hashed-code");
    }

    @Test
    @DisplayName("만료 시각 정각까지는 아직 유효하다")
    void notExpiredAtBoundary() {
        EmailOtpChallenge challenge = issued();

        assertThat(challenge.isExpired(EXPIRES_AT.minusSeconds(1))).isFalse();
        assertThat(challenge.isExpired(EXPIRES_AT)).isFalse();
        assertThat(challenge.isExpired(EXPIRES_AT.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("시도 횟수가 상한에 닿으면 막힌다")
    void blocksAtMaxAttempts() {
        EmailOtpChallenge challenge = issued();

        challenge.recordAttempt();
        challenge.recordAttempt();
        assertThat(challenge.isBlocked(3)).isFalse();

        challenge.recordAttempt();
        assertThat(challenge.isBlocked(3)).isTrue();
    }

    @Test
    @DisplayName("사용 표시는 되돌리지 않는다")
    void markUsedIsOneWay() {
        // 한 번 쓴 코드가 다시 통과하면 재사용 공격이 열린다.
        EmailOtpChallenge challenge = issued();

        challenge.markUsed();

        assertThat(challenge.isUsed()).isTrue();
    }

    @Test
    @DisplayName("복원은 저장된 사용 여부와 시도 횟수를 되살린다")
    void restoreKeepsStoredState() {
        EmailOtpChallenge challenge = EmailOtpChallenge.restore(7L, "user@example.com", "hashed-code",
                EXPIRES_AT, true, 2, NOW);

        assertThat(challenge.getId()).isEqualTo(7L);
        assertThat(challenge.isUsed()).isTrue();
        assertThat(challenge.getAttemptCount()).isEqualTo(2);
    }
}
