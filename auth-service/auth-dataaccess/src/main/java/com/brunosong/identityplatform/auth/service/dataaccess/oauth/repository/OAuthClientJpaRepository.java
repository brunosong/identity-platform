package com.brunosong.identityplatform.auth.service.dataaccess.oauth.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.oauth.entity.OAuthClientJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthClientJpaRepository extends JpaRepository<OAuthClientJpaEntity, String> {

    Optional<OAuthClientJpaEntity> findByClientIdAndRealm(String clientId, String realm);
}
