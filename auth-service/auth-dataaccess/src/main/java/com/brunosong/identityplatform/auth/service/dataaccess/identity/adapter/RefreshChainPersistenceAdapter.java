package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.RefreshChainRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.RefreshChainJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.repository.RefreshChainJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.RefreshChain;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link RefreshChainRepository} 영속성 어댑터.
 */
@Component
@RequiredArgsConstructor
public class RefreshChainPersistenceAdapter implements RefreshChainRepository {

    private final RefreshChainJpaRepository repository;

    @Override
    @Transactional
    public void save(RefreshChain chain) {
        repository.save(toEntity(chain));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshChain> findById(String familyId) {
        return repository.findById(familyId).map(RefreshChainPersistenceAdapter::toDomain);
    }

    /**
     * 트랜잭션을 따로 연다. 재사용을 발견한 요청은 곧바로 예외로 끝나는데, 같은 트랜잭션에서
     * 지우면 그 예외가 지운 것까지 되돌린다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revoke(String familyId) {
        repository.deleteById(familyId);
    }

    private static RefreshChainJpaEntity toEntity(RefreshChain chain) {
        RefreshChainJpaEntity e = new RefreshChainJpaEntity();
        e.setFamilyId(chain.getFamilyId());
        e.setRealm(chain.getRealm().name());
        e.setSubjectId(chain.getSubjectId());
        e.setCurrentJti(chain.getCurrentJti());
        e.setCreatedAt(chain.getCreatedAt());
        e.setExpiresAt(chain.getExpiresAt());
        return e;
    }

    private static RefreshChain toDomain(RefreshChainJpaEntity e) {
        return RefreshChain.restore(e.getFamilyId(), Realm.valueOf(e.getRealm()), e.getSubjectId(),
                e.getCurrentJti(), e.getCreatedAt(), e.getExpiresAt());
    }
}
