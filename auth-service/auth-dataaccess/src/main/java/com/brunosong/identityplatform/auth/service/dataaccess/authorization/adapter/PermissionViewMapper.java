package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzPermissionEntity;

/**
 * 권한 엔티티 → 읽기 모델 투영. 조회 어댑터와 검색 어댑터가 같은 모양을 내보내야 해서 여기 모은다.
 */
final class PermissionViewMapper {

    private PermissionViewMapper() {
    }

    static PermissionView toView(AuthzPermissionEntity entity) {
        return new PermissionView(
                entity.getPermissionId(),
                entity.getRealm(),
                entity.getPermissionCode(),
                entity.getPermissionName(),
                entity.getCategory(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt());
    }
}
