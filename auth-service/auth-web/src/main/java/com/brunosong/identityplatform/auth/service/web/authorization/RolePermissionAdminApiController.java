package com.brunosong.identityplatform.auth.service.web.authorization;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolePermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ReplaceRolePermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ToggleRolePermissionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RolePermissionMatrix;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RolePermissionsResult;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 역할×권한 매핑 운영 API. */
@RestController
@RequestMapping("/api/rbac")
@RequiredArgsConstructor
public class RolePermissionAdminApiController {

    private final ReplaceRolePermissionsUseCase replaceRolePermissions;
    private final ToggleRolePermissionUseCase toggleRolePermission;
    private final FindRolePermissionsUseCase findRolePermissions;
    private final RbacAdminAccess access;

    @GetMapping("/roles/{roleId}/permissions")
    public RolePermissionsResult forRole(HttpServletRequest request, @PathVariable Long roleId) {
        access.require(request);
        return findRolePermissions.forRole(roleId);
    }

    @PutMapping("/roles/{roleId}/permissions")
    public ResponseEntity<Void> replace(HttpServletRequest request,
                                        @PathVariable Long roleId,
                                        @Valid @RequestBody PermissionIdsRequest body) {
        access.require(request);
        replaceRolePermissions.replace(roleId, body.permissionIds());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/matrix")
    public RolePermissionMatrix matrix(HttpServletRequest request, @RequestParam Realm realm) {
        access.require(request);
        return findRolePermissions.matrix(realm);
    }

    @PostMapping("/matrix/toggle")
    public ResponseEntity<Void> toggle(HttpServletRequest request,
                                       @Valid @RequestBody ToggleRolePermissionRequest body) {
        access.require(request);
        toggleRolePermission.toggle(body.roleId(), body.permissionId(), body.assigned());
        return ResponseEntity.noContent().build();
    }

    public record PermissionIdsRequest(@NotNull List<Long> permissionIds) {
    }

    public record ToggleRolePermissionRequest(@NotNull Long roleId, @NotNull Long permissionId, boolean assigned) {
    }
}
