package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * 토큰을 받아 갈 클라이언트들. 키가 {@code clientId} 다.
     *
     * <pre>
     * token:
     *   clients:
     *     customer-portal:
     *       realm: PORTAL
     *       audiences: customer-service
     *     employee-admin:
     *       realm: ADMIN
     *       audiences: auth-service
     * </pre>
     *
     * <p>한 클라이언트가 audience 를 <b>여럿</b> 가질 수 있다는 것이 요점이다. 그래서 사용자는 한 번만
     * 로그인하고 그 토큰 하나로 나열된 서비스를 모두 쓴다 — 통합 로그인이 깨지지 않는다.
     * 대신 나열되지 않은 서비스는 그 토큰을 받지 않는다.
     */
    private Map<String, ClientProperties> clients = new LinkedHashMap<>();

    public static class ClientProperties {
        /** 이 클라이언트가 속한 realm. 다른 realm 으로 토큰을 요청하면 거부된다. */
        private Realm realm;
        /** 이 클라이언트의 토큰을 받아들일 서비스들. 토큰의 {@code aud} 가 된다. */
        private List<String> audiences = new ArrayList<>();

        public Realm getRealm() {
            return realm;
        }

        public void setRealm(Realm realm) {
            this.realm = realm;
        }

        public List<String> getAudiences() {
            return audiences;
        }

        public void setAudiences(List<String> audiences) {
            this.audiences = audiences;
        }
    }

    public static class RealmKeyProperties {
        private String kid;
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

    public Map<String, ClientProperties> getClients() {
        return clients;
    }

    public void setClients(Map<String, ClientProperties> clients) {
        this.clients = clients;
    }
}
