package com.brunosong.identityplatform.auth.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 소비 서비스가 auth 를 가리키는 설정.
 *
 * <pre>
 * auth:
 *   client:
 *     issuer: http://auth-service:8080/realms/portal
 *     cache-ttl: 10m
 *     warm-up: false
 * </pre>
 *
 * <p><b>줄 하나가 realm 경계 전부다.</b> 발급자 이름에 realm 이 들어 있고, 공개키를 받아올 주소도
 * 여기서 유도된다({@code issuer + "/.well-known/jwks.json"}). 그래서 "어느 서버의 어느 realm 인가"를
 * 한 값이 다 말한다 — Keycloak 의 {@code issuer-uri}, Spring Security 의
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} 와 같은 자리다.
 *
 * <p>이 값이 하는 일은 둘이다:
 * <ol>
 *   <li><b>키를 어디서 받나</b> — 주소로 쓴다. 그래서 다른 realm 의 공개키는 애초에 갖지 못한다.</li>
 *   <li><b>누가 만든 토큰인가</b> — 토큰의 {@code iss} 와 문자열로 대조한다. 서명은 "이 키를 가진
 *       누군가"까지만 말하고 어느 배포인지는 말하지 않는다. staging 과 prod 의 포털 토큰은 클레임이
 *       완전히 같아서, 주소를 잘못 가리키면 staging 계정으로 prod 가 열린다.</li>
 * </ol>
 *
 * <p>직원용과 고객용을 모두 제공해야 한다면 <b>같은 코드를 realm 별 설정으로 두 벌 띄우는</b> 것이
 * 표준적인 방법이다. 리소스 서버가 realm 하나에 속하는 편이 경계가 분명하다.
 */
@ConfigurationProperties(prefix = "auth.client")
public class AuthClientProperties {

    /**
     * 이 서비스가 상대하는 발급자 — {@code {auth 주소}/realms/{realm}}.
     * 토큰의 {@code iss} 가 이 값이어야 통과한다.
     */
    private String issuer;

    /**
     * JWKS 주소. 보통 비워 둔다 — {@code issuer + "/.well-known/jwks.json"} 으로 유도된다.
     *
     * <p>발급자 이름과 실제 주소가 다른 배치(리버스 프록시 뒤, 테스트의 랜덤 포트)에서만 명시한다.
     * {@code iss} 는 <b>식별자</b>라 문자열로 대조하고, 이 값은 <b>주소</b>라 실제로 접속한다.
     */
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

    /** 실제로 접속할 JWKS 주소. 명시하지 않았으면 발급자에서 유도한다. */
    public String resolveJwksUri() {
        if (jwksUri != null && !jwksUri.isBlank()) {
            return jwksUri;
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("auth.client.issuer 가 필요합니다.");
        }
        return issuer.replaceAll("/+$", "") + "/.well-known/jwks.json";
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
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
