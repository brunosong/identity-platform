package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

import com.brunosong.identityplatform.auth.service.domain.authorization.Role;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;

/** 역할 조회 결과. */
public record RoleView(Long roleId, Realm realm, String roleCode, String roleName,
                       String description, boolean active, List<Long> permissionIds) {

    public static RoleView from(Role role) {
        return new RoleView(role.getRoleId(), role.getRealm(), role.getRoleCode(), role.getRoleName(),
                role.getDescription(), role.isActive(), List.copyOf(role.getPermissionIds()));
    }
}
