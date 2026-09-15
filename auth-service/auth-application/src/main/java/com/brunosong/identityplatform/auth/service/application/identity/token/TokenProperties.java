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
     * 토큰을 받아 갈 앱들. 키가 {@code clientId} 다.
     *
     * <pre>
     * token:
     *   clients:
     *     customer-portal:
     *       realm: PORTAL
     *       system: portal
     *     employee-admin:
     *       realm: ADMIN
     *       system: admin
     * </pre>
     *
     * <p>앱은 <b>시스템 하나</b>를 상대한다. 그 시스템 안에 서비스가 몇 개인지는 앱도 토큰도 모른다.
     * 서비스를 붙여도 여기는 바뀌지 않고, 이미 발급된 토큰도 그대로 새 서비스에 닿는다.
     *
     * <p>시스템을 여럿 적지 않는다. 적으면 가장 약한 시스템이 전체의 보안 수준이 되고, 시스템을
     * 늘릴 때 설정을 고쳐야 하는 문제가 한 층 위로 옮겨갈 뿐이다. 통합 로그인은 세션이 맡는 일이다.
     * 한 번 인증하고 시스템마다 토큰을 따로 받는 것이 표준이고(OIDC, Keycloak), 토큰 하나를
     * 여러 시스템이 나눠 쓰는 것이 아니다.
     */
    private Map<String, ClientProperties> clients = new LinkedHashMap<>();

    public static class ClientProperties {
        /** 이 앱이 속한 realm. 다른 realm 으로 토큰을 요청하면 거부된다. */
        private Realm realm;
        /** 이 앱이 상대하는 시스템. 토큰의 {@code aud} 가 된다. */
        private String system;

        public Realm getRealm() {
            return realm;
        }

        public void setRealm(Realm realm) {
            this.realm = realm;
        }

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }
    }

    public static class RealmKeyProperties {
        /**
         * 이 realm 에 <b>있는 시스템들</b>. 앱의 {@code system} 은 여기 있는 것만 쓸 수 있다.
         *
         * <p>realm 하나에 시스템이 여럿일 수 있다. 다만 시스템은 realm 을 넘지 못한다. 신뢰하는
         * 발급자가 곧 realm 이기 때문이다. PORTAL 시스템을 ADMIN 앱에 적으면 그 토큰은 그
         * 시스템에 닿지도 못한다(서명과 발급자에서 죽는다). 부팅에서 잡지 않으면 401 만 보이고
         * 원인은 토큰 안에 있어 찾기 번거롭다. 오타도 같은 자리에서 걸린다.
         *
         * <p>여기 적는 것은 <b>시스템</b>이지 서비스가 아니다. 서비스 목록은 DB(authz_service)가
         * 쥐고 있어서, 서비스를 붙일 때 이 설정은 건드리지 않는다.
         */
        private List<String> systems = new ArrayList<>();

        private String kid;

        public List<String> getSystems() {
            return systems;
        }

        public void setSystems(List<String> systems) {
            this.systems = systems;
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

    public Map<String, ClientProperties> getClients() {
        return clients;
    }

    public void setClients(Map<String, ClientProperties> clients) {
        this.clients = clients;
    }
}
