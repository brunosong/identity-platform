package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

/**
 * 권한을 지운다.
 */
public interface DeletePermissionUseCase {

    void delete(Long permissionId);
}
