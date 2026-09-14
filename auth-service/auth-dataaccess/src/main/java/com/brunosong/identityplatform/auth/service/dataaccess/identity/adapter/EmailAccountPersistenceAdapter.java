package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.EmailAccountJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.repository.EmailAccountJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link EmailAccountRepository} 영속성 어댑터.
 */
@Component
@RequiredArgsConstructor
public class EmailAccountPersistenceAdapter implements EmailAccountRepository {

    private final EmailAccountJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailAccount> findByEmail(Realm realm, String email) {
        return repository.findByRealmAndEmail(realm.name(), email)
                .map(EmailAccountPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailAccount> findByPrincipalId(PrincipalId principalId) {
        return repository.findByPrincipalId(principalId.value())
                .map(EmailAccountPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public EmailAccount save(EmailAccount account) {
        EmailAccountJpaEntity e = new EmailAccountJpaEntity();
        e.setEmailAccountId(account.getEmailAccountId());
        e.setPrincipalId(account.getPrincipalId().value());
        e.setRealm(account.getRealm().name());
        e.setEmail(account.getEmail());
        e.setVerified(account.isVerified());
        e.setCreatedAt(account.getCreatedAt());
        return toDomain(repository.save(e));
    }

    private static EmailAccount toDomain(EmailAccountJpaEntity e) {
        return EmailAccount.restore(e.getEmailAccountId(), new PrincipalId(e.getPrincipalId()),
                Realm.valueOf(e.getRealm()), e.getEmail(), e.isVerified(), e.getCreatedAt());
    }
}
