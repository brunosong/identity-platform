package com.brunosong.identityplatform.order.service.web.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;
import java.util.Map;

/**
 * 토큰의 <b>이 서비스 칸</b>만 읽어 권한으로 옮긴다 — {@code resource_access.{audience}.roles}.
 *
 * <p>같은 토큰에 customer-service 의 칸도 함께 실려 있다. 통합 로그인이라 한 토큰이 두 서비스를
 * 다 상대하기 때문이다.
 *
 * <pre>
 * "realm_access":    { "roles": ["CUSTOMER"] }                        ← 영역 공통(여기선 안 읽는다)
 * "resource_access": {
 *     "customer-service": { "roles": ["PROFILE_READ"] },              ← 남의 칸
 *     "order-service":    { "roles": ["ORDER_READ", "ORDER_WRITE"] }  ← 이 칸만
 * }
 * </pre>
 *
 * <p>이 클래스가 자기 칸만 읽는 것이 <b>여기서 실제로 효력을 낸다.</b> 권한 어휘가 realm 전역
 * 평면 목록이었다면 한쪽 서비스의 {@code READ} 가 다른 쪽 문까지 열었을 것이다. 서비스가 둘이
 * 되고 나서야 그 차이가 눈에 보인다.
 *
 * <p>auth 는 역할 <b>이름</b>만 보관한다. 그 이름이 어떤 URL 을 여는지는 {@code SecurityConfiguration}
 * 이 정하고, 그 규칙은 이 서비스와 함께 배포된다.
 */
@Configuration
public class ClientRoleAuthorities {

    private static final String RESOURCE_ACCESS = "resource_access";
    private static final String ROLES = "roles";

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audience) {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> rolesFor(jwt, audience).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList());
        return converter;
    }

    @SuppressWarnings("unchecked")
    private static List<String> rolesFor(Jwt jwt, String audience) {
        Map<String, Object> byClient = jwt.getClaimAsMap(RESOURCE_ACCESS);
        if (byClient == null || !(byClient.get(audience) instanceof Map<?, ?> own)) {
            return List.of();
        }
        if (!(own.get(ROLES) instanceof List<?> roles)) {
            return List.of();
        }
        return ((List<Object>) roles).stream().map(String::valueOf).toList();
    }
}
