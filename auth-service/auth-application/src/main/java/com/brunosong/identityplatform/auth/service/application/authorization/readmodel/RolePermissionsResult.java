package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

import java.util.List;

/** 한 역할의 권한 편집 화면 데이터 — 역할 이름과 realm 전체 권한(배정 여부 포함). */
public record RolePermissionsResult(Long roleId, String roleName, List<RolePermissionView> permissions) {

    public RolePermissionsResult {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
