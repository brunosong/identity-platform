package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzPermissionEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzUrlAccessEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzPermissionJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlAccess;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * URL 규칙 엔티티 → 읽기 모델 투영. 조회 어댑터와 검색 어댑터가 같은 모양을 내보내야 해서 여기 모은다.
 *
 * <p>표시용 분류를 여기서 채운다. 페이지 호출 URL 은 패턴으로, 나머지는 매핑된 권한의 분류로 정한다.
 * 유도에 필요한 권한 분류표는 {@link #categoryByPermissionCode} 로 한 번만 읽어 목록 전체에 쓴다.
 */
final class UrlAccessViewMapper {

    private UrlAccessViewMapper() {
    }

    /** 권한코드 → 분류 표. 목록 하나를 만드는 동안 한 번만 읽는다. */
    static Map<String, String> categoryByPermissionCode(AuthzPermissionJpaRepository permissionRepository,
                                                        Realm realm) {
        Map<String, String> byCode = new LinkedHashMap<>();
        for (AuthzPermissionEntity permission
                : permissionRepository.findByRealmAndActiveIsTrueOrderByCategoryAscPermissionCodeAsc(realm)) {
            if (permission.getCategory() != null && !permission.getCategory().isBlank()) {
                byCode.putIfAbsent(permission.getPermissionCode(), permission.getCategory());
            }
        }
        return byCode;
    }

    static UrlAccessView toView(AuthzUrlAccessEntity entity, Map<String, String> categoryByCode) {
        List<String> permissionCodes = entity.getPermissions().stream()
                .map(AuthzPermissionEntity::getPermissionCode)
                .sorted()
                .toList();
        return new UrlAccessView(
                entity.getUrlAccessId(),
                entity.getRealm(),
                entity.getUrlPattern(),
                entity.getHttpMethod(),
                entity.getDescription(),
                entity.isActive(),
                entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                entity.getCreatedAt(),
                permissionCodes,
                categoryOf(entity, permissionCodes, categoryByCode));
    }

    private static String categoryOf(AuthzUrlAccessEntity entity, List<String> permissionCodes,
                                     Map<String, String> categoryByCode) {
        if (entity.getUrlPattern().startsWith(UrlAccess.PAGE_URL_PREFIX)) {
            return UrlAccess.PAGE_CATEGORY;
        }
        return permissionCodes.stream()
                .map(categoryByCode::get)
                .filter(category -> category != null && !category.isBlank())
                .findFirst()
                .orElse(null);
    }
}
