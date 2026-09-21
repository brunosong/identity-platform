package com.brunosong.identityplatform.auth.service.dataaccess.oauth.adapter;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.AuthorizationCodeRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.oauth.entity.OAuthAuthorizationCodeJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.oauth.repository.OAuthAuthorizationCodeJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link AuthorizationCodeRepository} 영속성 어댑터.
 *
 * <p>꺼내는 일이 곧 지우는 일이라 읽기 트랜잭션이 아니다. 값을 먼저 읽어 두고, 실제로 행을
 * 지운 경우에만 그 값을 내준다.
 */
@Component
@RequiredArgsConstructor
public class AuthorizationCodePersistenceAdapter implements AuthorizationCodeRepository {

    private final OAuthAuthorizationCodeJpaRepository repository;

    @Override
    @Transactional
    public void save(AuthorizationCode code) {
        repository.save(toEntity(code));
    }

    @Override
    @Transactional
    public Optional<AuthorizationCode> consume(String code) {
        Optional<OAuthAuthorizationCodeJpaEntity> found = repository.findById(code);
        if (found.isEmpty() || repository.deleteByCode(code) != 1) {
            return Optional.empty();
        }
        return found.map(AuthorizationCodePersistenceAdapter::toDomain);
    }

    private static OAuthAuthorizationCodeJpaEntity toEntity(AuthorizationCode code) {
        OAuthAuthorizationCodeJpaEntity e = new OAuthAuthorizationCodeJpaEntity();
        e.setCode(code.getCode());
        e.setRealm(code.getRealm().name());
        e.setClientId(code.getClientId());
        e.setRedirectUri(code.getRedirectUri());
        e.setCodeChallenge(code.getCodeChallenge());
        e.setPrincipalId(code.getPrincipalId().value());
        e.setScope(code.getScope());
        e.setNonce(code.getNonce());
        e.setExpiresAt(code.getExpiresAt());
        return e;
    }

    private static AuthorizationCode toDomain(OAuthAuthorizationCodeJpaEntity e) {
        return AuthorizationCode.restore(e.getCode(), Realm.valueOf(e.getRealm()), e.getClientId(),
                e.getRedirectUri(), e.getCodeChallenge(), new PrincipalId(e.getPrincipalId()),
                e.getScope(), e.getNonce(), e.getExpiresAt());
    }
}
