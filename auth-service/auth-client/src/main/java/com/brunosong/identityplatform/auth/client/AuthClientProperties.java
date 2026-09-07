package com.brunosong.identityplatform.auth.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 소비 서비스가 auth 를 가리키는 설정.
 *
 * <pre>
 * auth:
 *   client:
 *     jwks-uri: http://auth-service:8080/.well-known/jwks.json
 *     cache-ttl: 10m
 *     warm-up: false
 * </pre>
 */
@ConfigurationProperties(prefix = "auth.client")
public class AuthClientProperties {

    /** auth 의 JWKS 주소. 이것만 있으면 토큰을 검증할 수 있다. */
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
