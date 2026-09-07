package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.PrincipalJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.identity.repository.PrincipalJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link PrincipalRepository} 영속성 어댑터. identity_principal 테이블로 신원 애그리거트를 저장한다.
 * 역할(RBAC)은 이 애그리거트가 소유하지 않는다 — authz 도메인(authz_subject_role)이 단일 소스다.
 */
@Component
@RequiredArgsConstructor
public class PrincipalPersistenceAdapter implements PrincipalRepository {

    private final PrincipalJpaRepository principalRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Principal> findById(PrincipalId principalId) {
        return principalRepository.findById(principalId.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Principal> findBySubjectId(SubjectType subjectType, SubjectId subjectId) {
        return principalRepository.findBySubjectTypeAndSubjectId(subjectType, subjectId.value())
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public Principal save(Principal principal) {
        String pid = principal.getPrincipalId().value();
        PrincipalJpaEntity e = principalRepository.findById(pid).orElseGet(PrincipalJpaEntity::new);
        e.setPrincipalId(pid);
        e.setSubjectId(principal.getSubjectId().value());
        e.setSubjectType(principal.getSubjectType());
        e.setStatus(principal.getStatus());
        e.setLastAuthenticatedAt(principal.getLastAuthenticatedAt());
        principalRepository.save(e);
        return principal;
    }

    private Principal toDomain(PrincipalJpaEntity e) {
        return Principal.restore(
                new PrincipalId(e.getPrincipalId()),
                new SubjectId(e.getSubjectId()),
                e.getSubjectType(),
                e.getStatus(),
                e.getLastAuthenticatedAt());
    }
}
