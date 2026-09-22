package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.LoginSessionRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.LoginSessionJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.repository.LoginSessionJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.LoginSession;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link LoginSessionRepository} 영속성 어댑터.
 */
@Component
@RequiredArgsConstructor
public class LoginSessionPersistenceAdapter implements LoginSessionRepository {

    private final LoginSessionJpaRepository repository;

    @Override
    @Transactional
    public void save(LoginSession session) {
        repository.save(toEntity(session));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoginSession> findById(String sessionId) {
        return repository.findById(sessionId).map(LoginSessionPersistenceAdapter::toDomain);
    }

    private static LoginSessionJpaEntity toEntity(LoginSession session) {
        LoginSessionJpaEntity e = new LoginSessionJpaEntity();
        e.setSessionId(session.getSessionId());
        e.setRealm(session.getRealm().name());
        e.setPrincipalId(session.getPrincipalId().value());
        e.setCreatedAt(session.getCreatedAt());
        e.setExpiresAt(session.getExpiresAt());
        return e;
    }

    private static LoginSession toDomain(LoginSessionJpaEntity e) {
        return LoginSession.restore(e.getSessionId(), Realm.valueOf(e.getRealm()),
                new PrincipalId(e.getPrincipalId()), e.getCreatedAt(), e.getExpiresAt());
    }
}
