package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzPermissionEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzRoleEntity;

/**
 * 역할 엔티티 → 읽기 모델 투영. 조회 어댑터와 검색 어댑터가 같은 모양을 내보내야 해서 여기 모은다.
 */
final class RoleViewMapper {

    private RoleViewMapper() {
    }

    static RoleView toView(AuthzRoleEntity entity) {
        return new RoleView(
                entity.getRoleId(),
                entity.getRealm(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getPermissions().stream().map(AuthzPermissionEntity::getPermissionId).toList());
    }
}
