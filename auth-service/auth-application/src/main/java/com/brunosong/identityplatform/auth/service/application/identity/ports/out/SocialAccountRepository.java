package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.SocialAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Optional;

/**
 * 소셜 자격증명 저장 드리븐 포트.
 *
 * <p>조회에 realm 을 함께 받는다 — {@link PasswordAccountRepository}/{@link EmailAccountRepository} 와
 * 같은 규칙이다. realm 없이 찾으면 상대 realm 에 연결된 소셜 계정이 걸린다.
 */
public interface SocialAccountRepository {

    Optional<SocialAccount> findByProvider(Realm realm, SocialProvider provider, String providerUid);

    SocialAccount save(SocialAccount account);
}
