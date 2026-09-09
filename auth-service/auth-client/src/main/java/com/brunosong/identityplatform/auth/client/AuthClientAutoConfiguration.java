package com.brunosong.identityplatform.auth.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * 소비 서비스가 의존만 걸면 검증기가 준비되도록 하는 자동설정.
 *
 * <p>{@code auth.client.jwks-uri} 가 있을 때만 켜진다. 그 값이 없으면 auth 토큰을 쓰지 않는 서비스이므로
 * 빈을 만들지 않는다 — 자동설정이 조건 없이 켜지면 관계없는 서비스의 부팅을 깨뜨린다.
 *
 * <p>{@code auth.client.realm} 도 함께 있어야 한다. 없으면 {@link AuthTokenVerifier} 가 부팅에서
 * 실패한다 — realm 대조를 조용히 건너뛰는 것보다 뜨지 않는 편이 낫다.
 *
 * <p>{@link RestClient} 는 자기 것을 만든다. 소비 서비스의 공용 RestClient 를 가져다 쓰면 그쪽에 걸린
 * 인터셉터(예: 나가는 요청에 토큰을 붙이는 필터)가 JWKS 조회에까지 붙는다. 공개 엔드포인트에
 * 인증 헤더를 실을 이유가 없고, 순환(토큰을 얻으려 토큰이 필요한)을 만들 수도 있다.
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthClientProperties.class)
@ConditionalOnProperty(prefix = "auth.client", name = "jwks-uri")
public class AuthClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwksKeySource authJwksKeySource(AuthClientProperties properties) {
        JwksKeySource keySource = new JwksKeySource(
                RestClient.create(), properties.getJwksUri(), properties.getCacheTtl());
        if (properties.isWarmUp()) {
            keySource.warmUp();
        }
        return keySource;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthTokenVerifier authTokenVerifier(JwksKeySource keySource, AuthClientProperties properties) {
        return new AuthTokenVerifier(keySource, properties.getRealm());
    }
}
