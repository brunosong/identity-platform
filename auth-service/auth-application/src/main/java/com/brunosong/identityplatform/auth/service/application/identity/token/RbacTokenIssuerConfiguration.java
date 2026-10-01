package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.EnumMap;
import java.util.Map;

/**
 * 토큰 발급기 와이어링 — auth 가 token 설정을 직접 읽어 issuer 를 구성한다.
 *
 * <p>조건부가 아니다. 토큰 발급은 이 서비스의 본래 기능이라 켜고 끌 대상이 아니다 — 전에는 이 서비스가
 * 라이브러리였고 호스트가 {@code authorization.token.issuer=rbac} 로 켜는 선택 기능이었다.
 * 키가 설정에 없으면 부팅에서 실패한다. 토큰을 못 만드는 인증 서비스는 떠 있어도 소용이 없다.
 *
 * <p>realm 별 키페어와 그 realm 이 향할 시스템을 읽어 한 발급기에 넘긴다.
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
        for (Map.Entry<Realm, TokenProperties.RealmKeyProperties> entry : properties.getRealms().entrySet()) {
            Realm realm = entry.getKey();
            TokenProperties.RealmKeyProperties config = entry.getValue();
            if (config.getPrivateKey() == null || config.getPublicKey() == null) {
                throw new IllegalStateException(
                        "realm 키 설정이 불완전합니다(privateKey/publicKey): token.realms." + realm);
            }

            String kid = config.getKid() == null ? realm.name().toLowerCase() : config.getKid();
            PrivateKey privateKey = RsaKeys.privateKeyFromBase64Der(config.getPrivateKey());
            PublicKey publicKey = RsaKeys.publicKeyFromBase64Der(config.getPublicKey());
            keys.put(realm, new RealmSigningKeys.RealmKey(kid, privateKey, publicKey));
        }
        return new RealmSigningKeys(keys);
    }

    /**
     * realm 마다 향할 시스템을 읽고 부팅에서 검증한다.
     *
     * <p>{@code aud} 가 없는 토큰은 만들 이유가 없다. 없으면 발급자만 맞으면 누구든 받아들이게 되어,
     * 한 곳이 침해되면 그 토큰을 realm 의 다른 곳에 그대로 재생할 수 있다. 설정을 빠뜨렸을 때
     * 조용히 약해지는 것보다 부팅에서 죽는 편이 낫다.
     *
     * <p>검사 대상이 <b>시스템</b>이지 서비스가 아니다. 서비스 목록은 DB(authz_service)가 쥐고
     * 있어서, 서비스를 붙일 때 이 설정도 이 검사도 건드리지 않는다.
     */
    @Bean
    public RealmSystems realmSystems(TokenProperties properties) {
        Map<Realm, String> byRealm = new EnumMap<>(Realm.class);
        for (Map.Entry<Realm, TokenProperties.RealmKeyProperties> entry : properties.getRealms().entrySet()) {
            Realm realm = entry.getKey();
            String system = entry.getValue().getSystem();
            if (!StringUtils.hasText(system)) {
                throw new IllegalStateException("realm 에 system 이 없습니다: token.realms." + realm + ".system");
            }
            byRealm.put(realm, system.trim());
        }
        return new RealmSystems(byRealm);
    }

    @Bean
    public TokenIssuerPort rbacJwtTokenIssuer(
            RealmSigningKeys signingKeys,
            RealmIssuers issuers,
            RealmSystems systems,
            TokenProperties properties) {
        return new RbacJwtTokenIssuer(
                signingKeys, issuers, systems,
                properties.getAccessExpiration(), properties.getRefreshExpiration());
    }
}
