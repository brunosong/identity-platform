package com.brunosong.identityplatform.auth.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 소비 서비스가 auth 를 가리키는 설정.
 *
 * <pre>
 * auth:
 *   client:
 *     realm: PORTAL
 *     jwks-uri: http://auth-service:8080/realms/portal/.well-known/jwks.json
 *     cache-ttl: 10m
 *     warm-up: false
 * </pre>
 *
 * <p><b>한 서비스는 realm 하나만 상대한다.</b> JWKS 가 realm 별로 나뉘어 있으므로 여기 적은 주소의
 * 키만 갖게 되고, 다른 realm 의 토큰은 {@code kid} 를 찾지 못해 서명 검증에서 죽는다 — 이 서비스의
 * 코드가 한 줄도 돌기 전에.
 *
 * <p>직원용과 고객용을 모두 제공해야 한다면 <b>같은 코드를 realm 별 설정으로 두 벌 띄우는</b> 것이
 * 표준적인 방법이다. 리소스 서버가 realm 하나에 속하는 편이 경계가 분명하다.
 */
@ConfigurationProperties(prefix = "auth.client")
public class AuthClientProperties {

    /**
     * 이 서비스가 상대하는 realm. 토큰의 {@code realm} 클레임이 이 값이어야 통과한다.
     *
     * <p>JWKS 주소만으로도 다른 realm 은 걸러지지만, 이 값을 따로 두는 이유는 <b>주소를 잘못 가리켰을
     * 때 시끄럽게 실패하게</b> 하기 위해서다. 어드민 JWKS 를 가리켜 놓고 고객 서비스라고 믿는 상황을
     * 이 대조가 잡아낸다.
     */
    private String realm;

    /** auth 의 realm 별 JWKS 주소. */
    private String jwksUri;

    /**
     * 받아온 키를 이만큼 믿는다. 짧게 잡을 이유는 크지 않다 — 모르는 kid 를 만나면 TTL 과 무관하게
     * 다시 받아오므로, 키 교체는 이 값이 아니라 그 경로가 처리한다.
     */
    private Duration cacheTtl = Duration.ofMinutes(10);

    /**
     * 시작할 때 미리 받아올지. 기본은 받지 않는다 — auth 보다 먼저 떠도 부팅이 실패하면 안 되고,
     * 어차피 첫 검증 때 받아온다. 첫 요청 지연이 아쉬우면 켠다(실패해도 부팅은 계속된다).
     */
    private boolean warmUp = false;

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public boolean isWarmUp() {
        return warmUp;
    }

    public void setWarmUp(boolean warmUp) {
        this.warmUp = warmUp;
    }
}
