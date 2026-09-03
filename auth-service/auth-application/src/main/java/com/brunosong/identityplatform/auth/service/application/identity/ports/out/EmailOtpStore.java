package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.EmailOtpChallenge;

import java.time.Instant;
import java.util.Optional;

/**
 * 이메일 OTP 챌린지 저장 드리븐 포트. auth 가 OTP 발급/검증을 직접 소유하므로 저장소도 auth 소유다.
 */
public interface EmailOtpStore {

    EmailOtpChallenge save(EmailOtpChallenge challenge);

    /**
     * 실패 시도 1회를 기록한다. <b>호출자의 트랜잭션과 무관하게 커밋되어야 한다.</b>
     *
     * <p>검증 실패는 예외로 끝나고 그 예외는 인증 트랜잭션을 되돌린다. 시도 횟수를 같은 트랜잭션에서
     * 올리면 그 증가분도 함께 사라져 잠금이 영원히 걸리지 않는다 — 무제한 대입이 가능해진다.
     * 비밀번호 계정의 실패 기록과 같은 이유로 같은 방식을 쓴다.
     */
    void recordFailedAttempt(EmailOtpChallenge challenge);

    /** 검증용 — 해당 이메일의 최신 미사용 챌린지. */
    Optional<EmailOtpChallenge> findLatestUnused(String email);

    /** 쿨다운 판정용 — 해당 이메일의 최신 발급 시각. */
    Optional<Instant> findLatestCreatedAt(String email);
}
