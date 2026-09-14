package com.brunosong.identityplatform.auth.service.dataaccess.identity.repository;

import com.brunosong.identityplatform.auth.service.dataaccess.identity.entity.SocialAccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountJpaRepository extends JpaRepository<SocialAccountJpaEntity, String> {

    Optional<SocialAccountJpaEntity> findByRealmAndProviderAndProviderUid(
            String realm, String provider, String providerUid);
}
