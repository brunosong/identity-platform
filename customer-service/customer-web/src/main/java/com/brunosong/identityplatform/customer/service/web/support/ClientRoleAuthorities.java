package com.brunosong.identityplatform.customer.service.web.support;

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
 * 토큰의 <b>이 서비스 칸</b>만 읽어 권한으로 옮긴다: {@code resource_access.{app.service-id}.roles}.
 *
 * <h2>칸 이름은 aud 가 아니다</h2>
 * 전에는 칸 이름을 {@code jwt.audiences} 에서 읽었다. 그때는 aud 가 서비스 이름이라 같은 값이었다.
 * 지금 aud 는 <b>시스템</b>({@code portal})이고 칸 이름은 <b>서비스</b>({@code customer-service})라
 * 둘이 갈렸다. 그래서 {@code app.service-id} 로 따로 받는다.
 *
 * <h2>왜 자기 칸만 읽나</h2>
 * 전에는 auth 가 권한 코드를 평면 목록({@code authLs})으로 실었다. 어휘가 realm 전역이라 서비스가
 * 늘수록 이름이 부딪히고, <b>남의 서비스 권한이 이 서비스의 문을 열 수 있었다</b> —
 * order-service 의 {@code READ} 와 이 서비스의 {@code READ} 가 같은 문자열이면 구분할 방법이 없다.
 *
 * <p>이제 서비스로 갈려 있다. 이 클래스는 자기 칸만 읽으므로, 다른 서비스의 역할은 여기서
 * 권한이 되지 않는다.
 *
 * <pre>
 * "realm_access":    { "roles": ["CUSTOMER"] }                      ← 영역 공통(여기선 안 읽는다)
 * "resource_access": { "customer-service": { "roles": [...] } }     ← 이 칸만
 * </pre>
 *
 * <h2>이 이름이 무엇을 여는지는 이 서비스가 정한다</h2>
 * auth 는 역할 <b>이름</b>만 보관한다. 그 이름이 어떤 URL 을 여는지는 이 서비스의 코드
 * ({@code SecurityConfiguration} 의 matcher 나 {@code @PreAuthorize})가 정하고, 그 규칙은 이 서비스와
 * 함께 배포된다 — 엔드포인트를 추가하려고 auth 스키마를 건드리지 않는다.
 *
 * <h2>지금 이 서비스에는 역할 검사가 없다</h2>
 * {@code /api/customers/me} 는 <b>본인 것만</b> 다루고, 그 문지기는 역할이 아니라 소유권이다 —
 * 조회 키를 토큰의 {@code sub} 로만 잡는 것. 역할로 열고 닫을 것은 남의 데이터를 다루는 API 가
 * 생길 때(직원이 고객 프로필을 조회하는 화면 같은 것) 붙는다. 그때 auth 에 client role 을 한 줄
 * 등록하고 여기에 matcher 를 더하면 된다.
 */
@Configuration
public class ClientRoleAuthorities {

    private static final String RESOURCE_ACCESS = "resource_access";
    private static final String ROLES = "roles";

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(
            @Value("${app.service-id}") String serviceId) {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> rolesFor(jwt, serviceId).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList());
        return converter;
    }

    @SuppressWarnings("unchecked")
    private static List<String> rolesFor(Jwt jwt, String serviceId) {
        Map<String, Object> byService = jwt.getClaimAsMap(RESOURCE_ACCESS);
        if (byService == null || !(byService.get(serviceId) instanceof Map<?, ?> own)) {
            return List.of();
        }
        if (!(own.get(ROLES) instanceof List<?> roles)) {
            return List.of();
        }
        return ((List<Object>) roles).stream().map(String::valueOf).toList();
    }
}
