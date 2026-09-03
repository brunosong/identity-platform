package com.brunosong.identityplatform.auth.service.domain.identity;

import lombok.Getter;

import java.time.Instant;

/**
 * 이메일 OTP 챌린지(발급된 인증번호). 코드 원문은 저장하지 않고 해시로만 보관한다.
 * auth 가 OTP 발급/검증을 직접 소유한다(호스트의 자격증명 저장에 의존하지 않음).
 */
@Getter
public class EmailOtpChallenge {

    private final Long id;            // null = 신규(미저장)
    private final String email;
    private final String codeHash;
    private final Instant expiresAt;
    private boolean used;
    private int attemptCount;
    private final Instant createdAt;

    private EmailOtpChallenge(Long id, String email, String codeHash, Instant expiresAt,
                             boolean used, int attemptCount, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.used = used;
        this.attemptCount = attemptCount;
        this.createdAt = createdAt;
    }

    public static EmailOtpChallenge issue(String email, String codeHash, Instant now, Instant expiresAt) {
        return new EmailOtpChallenge(null, email, codeHash, expiresAt, false, 0, now);
    }

    public static EmailOtpChallenge restore(Long id, String email, String codeHash, Instant expiresAt,
                                            boolean used, int attemptCount, Instant createdAt) {
        return new EmailOtpChallenge(id, email, codeHash, expiresAt, used, attemptCount, createdAt);
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isBlocked(int maxAttempts) {
        return attemptCount >= maxAttempts;
    }

    public void markUsed() {
        this.used = true;
    }

    public void recordAttempt() {
        this.attemptCount++;
    }
}
