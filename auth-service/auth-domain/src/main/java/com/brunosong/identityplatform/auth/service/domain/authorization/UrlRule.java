package com.brunosong.identityplatform.auth.service.domain.authorization;

import com.brunosong.identityplatform.auth.service.domain.authorization.valueobject.HttpMethodPattern;

import java.util.Collection;
import java.util.Set;

/**
 * 접근제어 엔진 캐시 1건 — (URL 패턴, HTTP 메서드) 와 그 URL 을 허용하는 권한코드 집합(OR).
 * 매핑 중 하나라도 보유하면 허용한다. 불변 값 객체(프레임워크 비의존).
 *
 * <p>URL 패턴 매칭 자체는 담지 않는다. 패턴 문법(Ant/정규식)은 시행하는 쪽의 선택이라
 * 도메인이 특정 매처에 묶이지 않게 두고, 메서드 매칭과 권한 판정만 여기서 한다.
 */
public record UrlRule(String urlPattern, HttpMethodPattern httpMethod, Set<String> permissionCodes) {

    public UrlRule {
        if (urlPattern == null || urlPattern.isBlank()) {
            throw new IllegalArgumentException("urlPattern must not be blank");
        }
        if (httpMethod == null) {
            throw new IllegalArgumentException("httpMethod must not be null");
        }
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
    }

    public UrlRule(String urlPattern, HttpMethodPattern httpMethod, Collection<String> permissionCodes) {
        this(urlPattern, httpMethod, permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes));
    }

    /** 요청 메서드가 이 규칙의 대상인가. */
    public boolean matchesMethod(String requestMethod) {
        return httpMethod.matches(requestMethod);
    }

    /** 보유 권한 중 하나라도 이 규칙을 통과시키는가(OR). */
    public boolean allowsAny(Set<String> userPermissions) {
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }
        return permissionCodes.stream().anyMatch(userPermissions::contains);
    }
}
