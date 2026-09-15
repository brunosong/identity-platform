package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * 토큰 설정 — 만료와 realm 별 키페어.
 *
 * <pre>
 * token:
 *   issuer: http://localhost:8080
 *   accessExpiration: 7200000
 *   refreshExpiration: 86400000
 *   realms:
 *     ADMIN:
 *       kid: admin-1
 *       privateKey: (base64 PKCS#8 DER)
 *       publicKey:  (base64 X.509 DER)
 *     PORTAL:
 *       kid: portal-1
 *       ...
 * </pre>
 *
 * <p>키가 realm 별 맵인 것이 이 단계의 핵심이다. 전에는 {@code token.privateKey} 하나였고 프로세스가
 * realm 하나만 담당했다. 한 서비스가 두 realm 을 모두 발급하려면 키도 두 벌을 쥐어야 한다.
 *
 * <p>kid 는 생략하면 realm 이름을 소문자로 쓴다. 키 교체를 하게 되면 그때 명시적으로 준다.
 *
 * <p>{@code issuer} 는 이 서비스의 <b>바깥에서 보이는 주소</b>다. realm 이름이 뒤에 붙어 토큰의
 * {@code iss} 가 된다({@link RealmIssuers}). 포트를 옮기면 이 값도 함께 옮겨야 한다 — 소비 서비스는
 * 이 문자열을 그대로 대조하므로 {@code :8080} 과 {@code :8090} 은 다른 발급자다.
 */
@ConfigurationProperties(prefix = "token")
public class TokenProperties {

    /** 토큰에 실릴 발급자의 기준 주소. realm 이 뒤에 붙는다. */
    private String issuer = "http://localhost:8080";
    private long accessExpiration = 7_200_000L;
    private long refreshExpiration = 86_400_000L;
    private Map<Realm, RealmKeyProperties> realms = new EnumMap<>(Realm.class);

    public static class RealmKeyProperties {
        /**
         * 이 realm 의 토큰이 향하는 <b>시스템</b>. 토큰의 {@code aud} 가 된다.
         *
         * <p>시스템은 마이크로서비스의 집합이다. 그 안에 서비스가 몇 개인지는 토큰도 앱도 모르고
         * DB({@code authz_service})만 안다. 서비스를 붙여도 이 값은 바뀌지 않는다.
         *
         * <p><b>realm 당 하나다.</b> 한때 앱({@code clientId})이 시스템을 고르게 했는데, realm 과
         * 시스템이 1:1 이라 앱은 새 정보를 더하지 않으면서 "앱과 realm 이 어긋나는" 실패 모드만
         * 만들었다. realm 은 경로에 있으므로 여기서 바로 유도한다.
         *
         * <p>한 realm 에 시스템이 둘 이상 필요해지면 그때 고를 값을 다시 들인다. 그 값이 없으면
         * 경로만으로는 어느 시스템인지 정할 수 없기 때문이다.
         */
        private String system;

        private String kid;

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        private String privateKey;
        private String publicKey;

        public String getKid() {
            return kid;
        }

        public void setKid(String kid) {
            this.kid = kid;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessExpiration() {
        return accessExpiration;
    }

    public void setAccessExpiration(long accessExpiration) {
        this.accessExpiration = accessExpiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public void setRefreshExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
    }

    public Map<Realm, RealmKeyProperties> getRealms() {
        return realms;
    }

    public void setRealms(Map<Realm, RealmKeyProperties> realms) {
        this.realms = realms;
    }

}
