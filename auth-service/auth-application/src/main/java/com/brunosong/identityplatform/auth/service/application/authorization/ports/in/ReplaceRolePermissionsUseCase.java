package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import java.util.List;

/**
 * 역할의 권한 매핑을 입력 집합으로 통째로 바꾼다.
 */
public interface ReplaceRolePermissionsUseCase {

    void replace(Long roleId, List<Long> permissionIds);
}
