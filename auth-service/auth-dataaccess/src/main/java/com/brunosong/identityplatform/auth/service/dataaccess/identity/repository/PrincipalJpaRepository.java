package com.brunosong.identityplatform.auth.service.dataaccess.identity.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.PrincipalJpaEntity;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrincipalJpaRepository extends JpaRepository<PrincipalJpaEntity, String> {

    Optional<PrincipalJpaEntity> findBySubjectTypeAndSubjectId(SubjectType subjectType, String subjectId);
}
