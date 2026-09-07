package com.brunosong.identityplatform.auth.service.dataaccess.identity.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.PasswordAccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordAccountJpaRepository extends JpaRepository<PasswordAccountJpaEntity, String> {

    Optional<PasswordAccountJpaEntity> findBySubjectTypeAndLoginId(String subjectType, String loginId);

    boolean existsBySubjectTypeAndLoginId(String subjectType, String loginId);
}
