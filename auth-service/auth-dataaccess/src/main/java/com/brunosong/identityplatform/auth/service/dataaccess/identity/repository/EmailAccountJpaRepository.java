package com.brunosong.identityplatform.auth.service.dataaccess.identity.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.EmailAccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailAccountJpaRepository extends JpaRepository<EmailAccountJpaEntity, String> {

    Optional<EmailAccountJpaEntity> findByRealmAndEmail(String realm, String email);

    Optional<EmailAccountJpaEntity> findByPrincipalId(String principalId);
}
