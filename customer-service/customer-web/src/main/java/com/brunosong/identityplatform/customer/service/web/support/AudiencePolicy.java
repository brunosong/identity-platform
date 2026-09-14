package com.brunosong.identityplatform.customer.service.web.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.function.Supplier;

/**
 * 이 서비스 앞으로 발급된 토큰만 받아들인다 — {@code aud} 검증.
 *
 * <h2>발급자만 확인하면 부족하다</h2>
 * {@code issuer-uri} 만 두면 <b>그 realm 의 토큰이면 무엇이든 통한다.</b> 포털에 서비스가 여럿
 * 생기면 어느 서비스로 들어온 토큰이든 여기에 그대로 재생할 수 있다는 뜻이고, 그때부터
 * <b>가장 약한 서비스 하나가 realm 전체의 보안 수준</b>이 된다.
 *
 * <p>토큰이 "나는 customer-service 용" 이라고 말하면 그 재생이 막힌다. 그것이 {@code aud} 다.
 *
 * <h2>통합 로그인은 깨지지 않는다</h2>
 * auth 의 클라이언트 설정이 audience 를 <b>여럿</b> 가질 수 있기 때문이다. 고객 포털이
 * {@code [customer-service, order-service]} 를 가지면 사용자는 한 번 로그인해 그 토큰 하나로 둘 다
 * 쓴다. 달라지는 것은 나열되지 않은 서비스가 거부한다는 것뿐이다.
 *
 * <h2>왜 설정 한 줄로 안 되나</h2>
 * Spring Boot 3.4 부터는 {@code spring.security.oauth2.resourceserver.jwt.audiences} 프로퍼티가 있다.
 * 이 프로젝트는 3.3.5 라 디코더를 직접 만들어 검증기를 얹는다 — 기본 검증(서명·만료·발급자)은
 * {@link JwtValidators#createDefaultWithIssuer} 가 그대로 해 주고, 거기에 audience 하나를 더한다.
 *
 * <p><b>부팅할 때 auth 를 부르지 않는다.</b> {@link JwtDecoders#fromIssuerLocation} 은 discovery 문서를
 * 받아오므로, 그것을 {@link Supplier} 안에 넣어 첫 검증까지 미룬다. 기본 설정({@code issuer-uri})이
 * 갖던 성질을 잃지 않기 위해서다 — customer 가 auth 보다 먼저 떠도 된다.
 */
@Configuration
@ConditionalOnProperty(name = "app.audience")
public class AudiencePolicy {

    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties,
                                 @Value("${app.audience}") String audience) {
        String issuerUri = properties.getJwt().getIssuerUri();

        // 첫 검증 때 만든다. 부팅 시점에 auth 로 나가지 않는다.
        Supplier<JwtDecoder> lazy = new Supplier<>() {
            private volatile JwtDecoder delegate;

            @Override
            public JwtDecoder get() {
                if (delegate == null) {
                    synchronized (this) {
                        if (delegate == null) {
                            NimbusJwtDecoder decoder =
                                    (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
                            decoder.setJwtValidator(new DelegatingValidator(issuerUri, audience));
                            delegate = decoder;
                        }
                    }
                }
                return delegate;
            }
        };
        return token -> lazy.get().decode(token);
    }

    /** 기본 검증(서명·만료·발급자)에 audience 하나를 더한다. */
    private static final class DelegatingValidator
            implements OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> {

        private final OAuth2TokenValidator<Jwt> defaults;
        private final String audience;

        private DelegatingValidator(String issuerUri, String audience) {
            this.defaults = JwtValidators.createDefaultWithIssuer(issuerUri);
            this.audience = audience;
        }

        @Override
        public org.springframework.security.oauth2.core.OAuth2TokenValidatorResult validate(Jwt token) {
            var result = defaults.validate(token);
            if (result.hasErrors()) {
                return result;
            }
            if (token.getAudience() != null && token.getAudience().contains(audience)) {
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
            }
            // 왜 거부됐는지 자세히 알려주지 않는다. 필터가 401 로 끝내고 본문은 비어 있다.
            return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                    new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token", "이 서비스 앞으로 발급된 토큰이 아닙니다.", null));
        }
    }
}
