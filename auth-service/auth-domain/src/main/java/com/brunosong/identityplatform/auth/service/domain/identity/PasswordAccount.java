package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 비밀번호 자격증명(로그인ID/비밀번호). principalId 로 Principal 과 연결되는 인증 수단이다.
 * 이메일 OTP({@link EmailAccount})·소셜 등 다른 수단과 나란한 수단별 계정 애그리거트.
 *
 * <p>brute-force 방어를 위해 연속 실패 카운트와 잠금 만료 시각을 갖는다(이메일 OTP 의 시도횟수 잠금과 대칭).
 */
@Getter
public class PasswordAccount {

    private final String passwordAccountId;
    private final PrincipalId principalId;
    private final String loginId;
    private final String passwordHash;
    private final Instant createdAt;

    private int failedAttempts;
    private Instant lockedUntil;

    private PasswordAccount(String passwordAccountId, PrincipalId principalId, String loginId,
                            String passwordHash, Instant createdAt, int failedAttempts, Instant lockedUntil) {
        this.passwordAccountId = passwordAccountId;
        this.principalId = principalId;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }

    public static PasswordAccount create(PrincipalId principalId, String loginId, String passwordHash) {
        if (principalId == null) throw new IllegalArgumentException("principalId must not be null");
        if (loginId == null || loginId.isBlank()) throw new IllegalArgumentException("loginId must not be blank");
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        return new PasswordAccount(UUID.randomUUID().toString(), principalId, loginId, passwordHash,
                Instant.now(), 0, null);
    }

    public static PasswordAccount restore(String passwordAccountId, PrincipalId principalId, String loginId,
                                          String passwordHash, Instant createdAt,
                                          int failedAttempts, Instant lockedUntil) {
        return new PasswordAccount(passwordAccountId, principalId, loginId, passwordHash, createdAt,
                failedAttempts, lockedUntil);
    }

    /** 현재 잠긴 상태인가(잠금 만료 시각이 아직 지나지 않았는가). */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** 인증 성공 후 초기화할 실패 상태가 남아 있는가. */
    public boolean hasFailureState() {
        return failedAttempts > 0 || lockedUntil != null;
    }

    /**
     * 로그인 실패 1회 기록. 연속 실패가 maxAttempts 에 도달하면 lockDuration 만큼 잠그고 카운트를 리셋한다
     * (잠금 해제 후 다음 시도부터 새 창).
     */
    public void recordFailure(Instant now, int maxAttempts, Duration lockDuration) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedAttempts = 0;
        }
    }

    /** 인증 성공 시 실패 카운트/잠금 해제. */
    public void resetFailureState() {
        failedAttempts = 0;
        lockedUntil = null;
    }
}
