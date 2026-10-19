package com.brunosong.identityplatform.order.service.web.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealmRoleAuthoritiesTest {

    private final RealmRoleAuthorities converter = new RealmRoleAuthorities();

    @Test
    @DisplayName("realm_access.roles 의 역할마다 ROLE_ 을 붙여 권한으로 옮긴다")
    void rolesBecomeAuthorities() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("CUSTOMER", "VIP"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER", "ROLE_VIP");
    }

    @Test
    @DisplayName("realm_access 가 없는 토큰은 권한이 없다")
    void noRealmAccessMeansNoAuthorities() {
        Jwt jwt = token(Map.of());

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private static Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("customer-1")
                .claims(c -> c.putAll(claims))
                .build();
    }
}
