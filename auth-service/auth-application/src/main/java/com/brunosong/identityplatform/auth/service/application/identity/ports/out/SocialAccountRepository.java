package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.SocialAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;

import java.util.Optional;

/**
 * 소셜 자격증명 저장 드리븐 포트.
 */
public interface SocialAccountRepository {

    Optional<SocialAccount> findByProvider(SocialProvider provider, String providerUid);

    SocialAccount save(SocialAccount account);
}
