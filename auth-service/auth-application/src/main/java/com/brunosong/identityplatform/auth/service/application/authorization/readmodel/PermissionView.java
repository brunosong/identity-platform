package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

import com.brunosong.identityplatform.auth.service.domain.authorization.Permission;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.time.LocalDateTime;

/** 권한 조회 결과. */
public record PermissionView(Long permissionId, Realm realm, String permissionCode, String permissionName,
                             String category, String description, boolean active, LocalDateTime createdAt) {

    public static PermissionView from(Permission permission) {
        return new PermissionView(permission.getPermissionId(), permission.getRealm(),
                permission.getPermissionCode(), permission.getPermissionName(), permission.getCategory(),
                permission.getDescription(), permission.isActive(), permission.getCreatedAt());
    }
}
