package com.brunosong.identityplatform.customer.service.web.support;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 토큰의 {@code resource_access.<이 서비스>.roles} 를 Spring 의 권한으로 옮긴다. {@code PROFILE_WRITE} 는
 * 그대로 {@code PROFILE_WRITE} 가 되어 {@code hasAuthority("PROFILE_WRITE")} 로 검사한다.
 *
 * <pre>
 * "resource_access": {
 *     "customer-service": { "roles": ["PROFILE_READ", "PROFILE_WRITE"] },   ->   PROFILE_READ, PROFILE_WRITE
 *     "order-service":    { "roles": ["ORDER_READ"] }                       ->   읽지 않는다
 * }
 * </pre>
 *
 * <p>order-service 의 같은 이름 클래스와 같다. 공용 라이브러리로 묶지 않은 것은 두 서비스가 서로의
 * 배포를 기다리지 않게 하려는 것이다.
 *
 * <p><b>자기 칸만 읽는다.</b> 남의 서비스 칸까지 옮기면 이름이 같은 권한이 생기는 날 그쪽에 준 권한이
 * 이 서비스의 문을 연다.
 *
 * <p>{@code ROLE_} 을 붙이지 않는다. 역할이 아니라 권한이다. 역할({@code realm_access.roles})은 읽지
 * 않는다. 누가 이 기능을 쓸 수 있는지는 auth 가 역할과 권한을 이어서 정하고, 이 서비스는 자기 기능
 * 이름만 안다.
 */
public final class ServicePermissionAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String RESOURCE_ACCESS = "resource_access";
    private static final String ROLES = "roles";

    /** auth 의 authz_service.service_id 와 같은 값. */
    private final String serviceId;

    public ServicePermissionAuthorities(String serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // 권한을 싣기 전에 발급된 토큰에는 이 칸이 없다. 아무 권한이 없는 사람으로 본다.
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS);
        if (resourceAccess == null) {
            return List.of();
        }
        if (!(resourceAccess.get(serviceId) instanceof Map<?, ?> mine)) {
            return List.of();
        }
        if (!(mine.get(ROLES) instanceof List<?> codes)) {
            return List.of();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Object code : codes) {
            authorities.add(new SimpleGrantedAuthority(code.toString()));
        }
        return authorities;
    }
}
