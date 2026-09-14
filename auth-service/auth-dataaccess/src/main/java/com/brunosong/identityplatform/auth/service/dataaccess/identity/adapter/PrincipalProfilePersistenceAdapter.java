package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalProfileRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.PrincipalProfileJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.repository.PrincipalProfileJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.PrincipalProfile;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** {@link PrincipalProfileRepository} 영속성 어댑터. */
@Component
@RequiredArgsConstructor
public class PrincipalProfilePersistenceAdapter implements PrincipalProfileRepository {

    private final PrincipalProfileJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PrincipalProfile> findByPrincipalId(PrincipalId principalId) {
        return repository.findById(principalId.value())
                .map(PrincipalProfilePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public PrincipalProfile save(PrincipalProfile profile) {
        PrincipalProfileJpaEntity e = new PrincipalProfileJpaEntity();
        e.setPrincipalId(profile.getPrincipalId().value());
        e.setName(profile.getName());
        e.setPhoneNumber(profile.getPhoneNumber());
        e.setCreatedAt(profile.getCreatedAt());
        e.setUpdatedAt(profile.getUpdatedAt());
        return toDomain(repository.save(e));
    }

    private static PrincipalProfile toDomain(PrincipalProfileJpaEntity e) {
        return PrincipalProfile.restore(new PrincipalId(e.getPrincipalId()), e.getName(),
                e.getPhoneNumber(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
