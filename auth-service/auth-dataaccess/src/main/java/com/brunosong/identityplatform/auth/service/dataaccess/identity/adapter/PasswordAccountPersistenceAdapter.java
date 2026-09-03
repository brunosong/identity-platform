package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordAccountRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.PasswordAccountJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.repository.PasswordAccountJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link PasswordAccountRepository} 영속성 어댑터.
 */
@Component
@RequiredArgsConstructor
public class PasswordAccountPersistenceAdapter implements PasswordAccountRepository {

    private final PasswordAccountJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PasswordAccount> findByLoginId(String loginId) {
        return repository.findByLoginId(loginId).map(PasswordAccountPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByLoginId(String loginId) {
        return repository.existsByLoginId(loginId);
    }

    @Override
    @Transactional
    public PasswordAccount save(PasswordAccount account) {
        return toDomain(repository.save(toEntity(account)));
    }

    /**
     * 잠금 상태 갱신은 별도 트랜잭션(REQUIRES_NEW)에서 커밋한다 — 실패 기록이 인증 트랜잭션 롤백에
     * 휩쓸려 사라지지 않게 한다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLoginState(PasswordAccount account) {
        repository.save(toEntity(account));
    }

    private static PasswordAccountJpaEntity toEntity(PasswordAccount account) {
        PasswordAccountJpaEntity e = new PasswordAccountJpaEntity();
        e.setPasswordAccountId(account.getPasswordAccountId());
        e.setPrincipalId(account.getPrincipalId().value());
        e.setLoginId(account.getLoginId());
        e.setPasswordHash(account.getPasswordHash());
        e.setCreatedAt(account.getCreatedAt());
        e.setFailedAttempts(account.getFailedAttempts());
        e.setLockedUntil(account.getLockedUntil());
        return e;
    }

    private static PasswordAccount toDomain(PasswordAccountJpaEntity e) {
        return PasswordAccount.restore(e.getPasswordAccountId(), new PrincipalId(e.getPrincipalId()),
                e.getLoginId(), e.getPasswordHash(), e.getCreatedAt(),
                e.getFailedAttempts(), e.getLockedUntil());
    }
}
