package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

import com.brunosong.identityplatform.auth.service.domain.authorization.UrlAccess;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.time.LocalDateTime;
import java.util.List;

/**
 * URL 접근 규칙 조회 결과.
 *
 * <p>{@code category} 는 저장된 값이 아니라 조회 시점에 매핑 권한에서 유도한 표시용 분류다
 * (/page 로 시작하면 PAGE, 아니면 매핑된 권한의 분류).
 */
public record UrlAccessView(Long urlAccessId, Realm realm, String urlPattern, String httpMethod,
                            String description, boolean active, int sortOrder, LocalDateTime createdAt,
                            List<String> permissionCodes, String category) {

    public static UrlAccessView from(UrlAccess urlAccess, String category) {
        return new UrlAccessView(urlAccess.getUrlAccessId(), urlAccess.getRealm(), urlAccess.getUrlPattern(),
                urlAccess.getHttpMethod().value(), urlAccess.getDescription(), urlAccess.isActive(),
                urlAccess.getSortOrder(), urlAccess.getCreatedAt(),
                List.copyOf(urlAccess.getPermissionCodes()), category);
    }

    public static UrlAccessView from(UrlAccess urlAccess) {
        return from(urlAccess, null);
    }
}
