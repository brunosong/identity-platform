package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

/** 역할 편집 화면 한 줄 — 권한 하나와 그 역할에 배정됐는지 여부. */
public record RolePermissionView(Long permissionId, String permissionCode, String permissionName,
                                 String category, boolean assigned) {

    public static RolePermissionView of(PermissionView permission, boolean assigned) {
        return new RolePermissionView(permission.permissionId(), permission.permissionCode(),
                permission.permissionName(), permission.category(), assigned);
    }
}
