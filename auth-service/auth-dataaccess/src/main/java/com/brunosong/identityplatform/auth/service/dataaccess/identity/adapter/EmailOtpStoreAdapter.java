package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailOtpStore;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.EmailOtpJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.repository.EmailOtpJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailOtpChallenge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * {@link EmailOtpStore} 영속성 어댑터. auth_email_otp 테이블로 OTP 챌린지를 저장/조회한다.
 */
@Component
@RequiredArgsConstructor
public class EmailOtpStoreAdapter implements EmailOtpStore {

    private final EmailOtpJpaRepository repository;

    @Override
    @Transactional
    public EmailOtpChallenge save(EmailOtpChallenge challenge) {
        EmailOtpJpaEntity e = challenge.getId() != null
                ? repository.findById(challenge.getId()).orElseGet(EmailOtpJpaEntity::new)
                : new EmailOtpJpaEntity();
        e.setEmail(challenge.getEmail());
        e.setCodeHash(challenge.getCodeHash());
        e.setExpiresAt(challenge.getExpiresAt());
        e.setUsed(challenge.isUsed());
        e.setAttemptCount(challenge.getAttemptCount());
        if (challenge.getCreatedAt() != null) {
            e.setCreatedAt(challenge.getCreatedAt());
        }
        return toDomain(repository.save(e));
    }

    /**
     * 실패 시도 기록은 별도 트랜잭션(REQUIRES_NEW)에서 커밋한다 — 인증 실패로 호출자 트랜잭션이
     * 롤백돼도 시도 횟수는 남아야 잠금이 동작한다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(EmailOtpChallenge challenge) {
        save(challenge);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailOtpChallenge> findLatestUnused(String email) {
        return repository.findFirstByEmailAndUsedFalseOrderByIdDesc(email).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findLatestCreatedAt(String email) {
        return repository.findFirstByEmailOrderByIdDesc(email).map(EmailOtpJpaEntity::getCreatedAt);
    }

    private EmailOtpChallenge toDomain(EmailOtpJpaEntity e) {
        return EmailOtpChallenge.restore(e.getId(), e.getEmail(), e.getCodeHash(),
                e.getExpiresAt(), e.isUsed(), e.getAttemptCount(), e.getCreatedAt());
    }
}
