package com.brunosong.identityplatform.auth.service.dataaccess.oauth.adapter;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.AuthorizationCodeRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.oauth.entity.OAuthAuthorizationCodeJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.oauth.repository.OAuthAuthorizationCodeJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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

    /**
     * 독립 트랜잭션(REQUIRES_NEW)에서 지운다. <b>호출자가 실패해도 코드는 태워진다.</b>
     *
     * <p>이게 없으면 교환에 실패한 코드가 되살아난다. 호출자는 꺼낸 뒤에 만료와 임자를 확인하고
     * 어긋나면 예외를 던지는데, 그 예외가 트랜잭션을 롤백시키면서 삭제까지 되돌리기 때문이다.
     * 그러면 PKCE 원본을 틀린 요청 하나가 코드를 무효화하지 못하고, 코드는 1분 내내 살아 있다.
     *
     * <p>실패 기록을 인증 트랜잭션 롤백에서 지켜내는 {@code PasswordAccountPersistenceAdapter}
     * 의 잠금 갱신과 같은 이유다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
