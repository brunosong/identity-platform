package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.GetAuthorizationRevisionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SessionRegistryPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 토큰 발급기 와이어링 — auth 가 token 설정을 직접 읽어 issuer 를 구성한다.
 *
 * <p>조건부가 아니다. 토큰 발급은 이 서비스의 본래 기능이라 켜고 끌 대상이 아니다 — 전에는 이 서비스가
 * 라이브러리였고 호스트가 {@code authorization.token.issuer=rbac} 로 켜는 선택 기능이었다.
 * 키가 설정에 없으면 부팅에서 실패한다. 토큰을 못 만드는 인증 서비스는 떠 있어도 소용이 없다.
 *
 * <p>realm 별 키페어를 모두 읽어 한 발급기에 넘긴다. 토큰에 실을 권한과 리비전은 auth 자체
 * ({@link ListSubjectRolesUseCase}/{@link GetAuthorizationRevisionUseCase})가 낸다.
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

    /**
     * 앱 설정을 부팅에서 검증한다.
     *
     * <p><b>시스템은 realm 에 속한다.</b> 신뢰하는 발급자가 곧 realm 이기 때문이다. 그래서 PORTAL
     * 시스템을 ADMIN 앱에 적으면 그 토큰은 그 시스템에 닿지도 못한다(서명과 발급자에서 죽는다).
     * 여기서 잡지 않으면 운영에서는 401 만 보이고 원인은 토큰 안에 있어 찾기 번거롭다.
     * 시스템 이름 오타도 같은 자리에서 걸린다.
     *
     * <p>검사 대상이 <b>시스템</b>이지 서비스가 아니다. 서비스 목록은 DB(authz_service)가 쥐고
     * 있어서, 서비스를 붙일 때 이 설정도 이 검사도 건드리지 않는다.
     */
    @Bean
    public TokenClients tokenClients(TokenProperties properties) {
        properties.getClients().forEach((clientId, client) -> {
            if (client.getRealm() == null) {
                throw new IllegalStateException("앱에 realm 이 없습니다: token.clients." + clientId);
            }
            if (!StringUtils.hasText(client.getSystem())) {
                throw new IllegalStateException("앱에 system 이 없습니다: token.clients." + clientId);
            }
            TokenProperties.RealmKeyProperties realm = properties.getRealms().get(client.getRealm());
            List<String> known = realm == null ? List.of() : realm.getSystems();
            if (!known.contains(client.getSystem())) {
                throw new IllegalStateException(
                        "이 realm 에 없는 system 입니다: token.clients." + clientId
                                + ".system=" + client.getSystem() + " (realm=" + client.getRealm()
                                + ", 이 realm 의 시스템=" + known + ")");
            }
        });
        return new TokenClients(properties.getClients());
    }

    @Bean
    public TokenIssuerPort rbacJwtTokenIssuer(
            ListSubjectRolesUseCase subjectRoles,
            GetAuthorizationRevisionUseCase revision,
            ObjectProvider<SessionRegistryPort> sessionRegistryProvider,
            RealmSigningKeys signingKeys,
            RealmIssuers issuers,
            TokenClients clients,
            TokenProperties properties) {
        return new RbacJwtTokenIssuer(
                subjectRoles, revision, sessionRegistryProvider, signingKeys, issuers, clients,
                properties.getAccessExpiration(), properties.getRefreshExpiration());
    }
}
