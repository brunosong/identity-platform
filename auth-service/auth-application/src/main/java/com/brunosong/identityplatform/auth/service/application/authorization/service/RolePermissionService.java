package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolePermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ReplaceRolePermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ToggleRolePermissionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionRepository;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleRepository;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RolePermissionAssignment;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RolePermissionMatrix;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RolePermissionView;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RolePermissionsResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.authorization.Role;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 역할과 권한의 매핑에 관한 유스케이스 구현. 둘 사이의 관계라 어느 한쪽 서비스에 붙이지 않는다.
 *
 * <p>편집은 역할 애그리거트가 판정하고({@link RoleRepository}), 화면 데이터는 읽기 포트가 바로 준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RolePermissionService implements ReplaceRolePermissionsUseCase, ToggleRolePermissionUseCase,
        FindRolePermissionsUseCase {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleQuery roleQuery;
    private final PermissionQuery permissionQuery;

    @Override
    @Transactional
    public void replace(Long roleId, List<Long> permissionIds) {
        roleRepository.replacePermissions(roleId, permissionIds == null ? List.of() : permissionIds);
    }

    /**
     * 부여/회수는 역할 애그리거트가 판정한다. 이미 그 상태면 저장까지 가지 않는다 —
     * 무의미한 쓰기가 매핑 테이블을 다시 쓰는 것을 막는다.
     */
    @Override
    @Transactional
    public void toggle(Long roleId, Long permissionId, boolean assigned) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthorizationNotFoundException.role(roleId));
        permissionRepository.findById(permissionId)
                .orElseThrow(() -> AuthorizationNotFoundException.permission(permissionId));

        boolean changed = assigned ? role.grantPermission(permissionId) : role.revokePermission(permissionId);
        if (!changed) {
            return;
        }
        roleRepository.replacePermissions(roleId, role.getPermissionIds());
    }

    @Override
    public RolePermissionsResult forRole(Long roleId) {
        RoleView role = roleQuery.findById(roleId)
                .orElseThrow(() -> AuthorizationNotFoundException.role(roleId));

        List<RolePermissionView> permissions = permissionQuery.listActive(role.realm()).stream()
                .map(permission -> RolePermissionView.of(permission,
                        role.permissionIds().contains(permission.permissionId())))
                .toList();
        return new RolePermissionsResult(role.roleId(), role.roleName(), permissions);
    }

    @Override
    public RolePermissionMatrix matrix(Realm realm) {
        List<RoleView> roles = roleQuery.listActive(realm);
        List<PermissionView> permissions = permissionQuery.listActive(realm);

        List<RolePermissionAssignment> assignments = new ArrayList<>();
        for (RoleView role : roles) {
            for (Long permissionId : role.permissionIds()) {
                assignments.add(new RolePermissionAssignment(role.roleId(), permissionId));
            }
        }
        return new RolePermissionMatrix(roles, permissions, assignments);
    }
}
