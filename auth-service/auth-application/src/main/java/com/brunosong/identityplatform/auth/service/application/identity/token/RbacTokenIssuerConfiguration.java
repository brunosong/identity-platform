package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.GetAuthorizationRevisionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SessionRegistryPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * 토큰 발급기 와이어링 — auth 가 token 설정을 직접 읽어 issuer 를 구성한다.
 *
 * <p>조건부가 아니다. 토큰 발급은 이 서비스의 본래 기능이라 켜고 끌 대상이 아니다 — 전에는 이 서비스가
 * 라이브러리였고 호스트가 {@code authorization.token.issuer=rbac} 로 켜는 선택 기능이었다.
 * 키가 설정에 없으면 부팅에서 실패한다. 토큰을 못 만드는 인증 서비스는 떠 있어도 소용이 없다.
 *
 * <p>realm 별 키페어를 모두 읽어 한 발급기에 넘긴다. 토큰에 실을 권한과 리비전은 auth 자체
 * ({@link ListSubjectPermissionsUseCase}/{@link GetAuthorizationRevisionUseCase})가 낸다.
 */
@Configuration
@EnableConfigurationProperties(TokenProperties.class)
public class RbacTokenIssuerConfiguration {

    @Bean
    public RealmIssuers realmIssuers(TokenProperties properties) {
        return new RealmIssuers(properties.getIssuer());
    }

    @Bean
    public RealmSigningKeys realmSigningKeys(TokenProperties properties) {
        Map<Realm, RealmSigningKeys.RealmKey> keys = new EnumMap<>(Realm.class);
        properties.getRealms().forEach((realm, config) -> {
            if (config.getPrivateKey() == null || config.getPublicKey() == null) {
                throw new IllegalStateException(
                        "realm 키 설정이 불완전합니다(privateKey/publicKey): token.realms." + realm);
            }
            String kid = config.getKid() == null ? realm.name().toLowerCase() : config.getKid();
            keys.put(realm, new RealmSigningKeys.RealmKey(
                    kid,
                    RsaKeys.privateKeyFromBase64Der(config.getPrivateKey()),
                    RsaKeys.publicKeyFromBase64Der(config.getPublicKey())));
        });
        return new RealmSigningKeys(keys);
    }

    @Bean
    public TokenClients tokenClients(TokenProperties properties) {
        properties.getClients().forEach((clientId, client) -> {
            if (client.getRealm() == null) {
                throw new IllegalStateException("클라이언트에 realm 이 없습니다: token.clients." + clientId);
            }
        });
        return new TokenClients(properties.getClients());
    }

    @Bean
    public TokenIssuerPort rbacJwtTokenIssuer(
            ListSubjectPermissionsUseCase subjectPermissions,
            GetAuthorizationRevisionUseCase revision,
            ObjectProvider<SessionRegistryPort> sessionRegistryProvider,
            RealmSigningKeys signingKeys,
            RealmIssuers issuers,
            TokenClients clients,
            TokenProperties properties) {
        return new RbacJwtTokenIssuer(
                subjectPermissions, revision, sessionRegistryProvider, signingKeys, issuers, clients,
                properties.getAccessExpiration(), properties.getRefreshExpiration());
    }
}
