package com.brunosong.identityplatform.auth.service.application.authorization.readmodel;

import java.util.List;

/**
 * realm 의 역할×권한 매트릭스. 축(역할/권한)과 교차점(배정)을 따로 준다 —
 * 역할마다 권한 전체를 복제해 보내면 역할 수×권한 수만큼 응답이 커진다.
 */
public record RolePermissionMatrix(List<RoleView> roles, List<PermissionView> permissions,
                                   List<RolePermissionAssignment> assignments) {

    public RolePermissionMatrix {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }
}
