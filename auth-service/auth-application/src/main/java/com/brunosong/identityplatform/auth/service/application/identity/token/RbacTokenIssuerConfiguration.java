package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.GetAuthorizationRevisionUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SessionRegistryPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RBAC 토큰 발급기 와이어링 — auth 가 token 설정을 직접 읽어 issuer 를 구성한다(호스트엔 토큰 와이어링 코드 없음).
 *
 * <p>호스트가 {@code authorization.token.issuer=rbac} 로 켜면 활성화된다. realm 은 {@code authorization.realm},
 * RSA 키페어(base64 DER)와 만료는 {@code token.*} 에서 읽는다(호스트마다 다른 realm 키페어).
 * 토큰에 실을 권한과 리비전은 auth 자체({@link ListSubjectPermissionsUseCase}/{@link GetAuthorizationRevisionUseCase})가
 * 낸다. 이 프로퍼티를 켜지 않는 호스트는 이 빈이 생성되지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = "authorization.token.issuer", havingValue = "rbac")
public class RbacTokenIssuerConfiguration {

    @Bean
    public TokenIssuerPort rbacJwtTokenIssuer(
            EmailAccountRepository emailAccountRepository,
            ListSubjectPermissionsUseCase subjectPermissions,
            GetAuthorizationRevisionUseCase revision,
            ObjectProvider<SessionRegistryPort> sessionRegistryProvider,
            @Value("${authorization.realm}") Realm realm,
            @Value("${token.privateKey}") String privateKey,
            @Value("${token.publicKey}") String publicKey,
            @Value("${token.accessExpiration}") long accessExpiration,
            @Value("${token.refreshExpiration}") long refreshExpiration) {
        return new RbacJwtTokenIssuer(
                realm, emailAccountRepository, subjectPermissions, revision, sessionRegistryProvider,
                RsaKeys.privateKeyFromBase64Der(privateKey), RsaKeys.publicKeyFromBase64Der(publicKey),
                accessExpiration, refreshExpiration);
    }
}
