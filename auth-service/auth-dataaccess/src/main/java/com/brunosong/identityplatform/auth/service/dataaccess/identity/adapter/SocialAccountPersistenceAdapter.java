package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialAccountRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.SocialAccountJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.repository.SocialAccountJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link SocialAccountRepository} 영속성 어댑터.
 */
@Component
@RequiredArgsConstructor
public class SocialAccountPersistenceAdapter implements SocialAccountRepository {

    private final SocialAccountJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<SocialAccount> findByProvider(Realm realm, SocialProvider provider,
                                                 String providerUid) {
        return repository
                .findByRealmAndProviderAndProviderUid(realm.name(), provider.name(), providerUid)
                .map(SocialAccountPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public SocialAccount save(SocialAccount account) {
        SocialAccountJpaEntity e = new SocialAccountJpaEntity();
        e.setSocialAccountId(account.getSocialAccountId());
        e.setPrincipalId(account.getPrincipalId().value());
        e.setRealm(account.getRealm().name());
        e.setProvider(account.getProvider().name());
        e.setProviderUid(account.getProviderUid());
        e.setCreatedAt(account.getCreatedAt());
        return toDomain(repository.save(e));
    }

    private static SocialAccount toDomain(SocialAccountJpaEntity e) {
        return SocialAccount.restore(e.getSocialAccountId(), new PrincipalId(e.getPrincipalId()),
                Realm.valueOf(e.getRealm()), SocialProvider.valueOf(e.getProvider()),
                e.getProviderUid(), e.getCreatedAt());
    }
}
