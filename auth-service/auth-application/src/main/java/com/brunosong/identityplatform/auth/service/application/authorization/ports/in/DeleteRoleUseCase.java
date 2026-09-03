package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

/**
 * 역할을 지운다.
 */
public interface DeleteRoleUseCase {

    void delete(Long roleId);
}
