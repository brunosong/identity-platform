package com.brunosong.identityplatform.order.service.web.support;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 토큰의 {@code realm_access.roles} 를 Spring 의 권한으로 옮긴다. {@code CUSTOMER} 는
 * {@code ROLE_CUSTOMER} 가 되어 {@code hasRole("CUSTOMER")} 로 검사할 수 있다.
 *
 * <pre>
 * "realm_access": { "roles": ["CUSTOMER"] }   ->   ROLE_CUSTOMER
 * </pre>
 *
 * <p>Spring 은 기본으로 {@code scope} 클레임만 읽는다. 이 칸은 Keycloak 의 모양이라 직접 꺼낸다.
 *
 * <p>서비스별 권한({@code resource_access.order-service.roles})은 auth 가 아직 싣지 않는다.
 * 그것이 실리면 이 서비스는 역할 대신 그 칸의 권한으로 판정하게 된다.
 */
public final class RealmRoleAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // 역할을 싣기 전에 발급된 토큰에는 이 칸이 없다. 역할이 없는 사람으로 본다.
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if (realmAccess == null) {
            return List.of();
        }
        if (!(realmAccess.get(ROLES) instanceof List<?> roles)) {
            return List.of();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Object role : roles) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
        }
        return authorities;
    }
}
