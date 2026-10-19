package com.brunosong.identityplatform.customer.service.web.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServicePermissionAuthoritiesTest {

    private final ServicePermissionAuthorities converter = new ServicePermissionAuthorities("customer-service");

    @Test
    @DisplayName("자기 서비스 칸의 권한만 그대로 옮긴다")
    void onlyOwnServicePermissions() {
        Jwt jwt = token(Map.of(
                "realm_access", Map.of("roles", List.of("CUSTOMER")),
                "resource_access", Map.of(
                        "customer-service", Map.of("roles", List.of("PROFILE_READ", "PROFILE_WRITE")),
                        "order-service", Map.of("roles", List.of("ORDER_READ")))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("PROFILE_READ", "PROFILE_WRITE");
    }

    @Test
    @DisplayName("자기 칸이 없으면 다른 서비스 권한이 있어도 권한이 없다")
    void otherServicesOnly() {
        Jwt jwt = token(Map.of("resource_access",
                Map.of("order-service", Map.of("roles", List.of("ORDER_READ")))));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("resource_access 가 없는 토큰은 권한이 없다")
    void noResourceAccessMeansNoAuthorities() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("CUSTOMER"))));

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
