package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

/**
 * 역할에 권한 하나를 붙이거나 뗀다. 이미 그 상태면 아무 일도 하지 않는다.
 */
public interface ToggleRolePermissionUseCase {

    void toggle(Long roleId, Long permissionId, boolean assigned);
}
