package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.domain.authorization.Permission;

import java.util.Optional;

/**
 * 권한 쓰기 포트(SPI) — 애그리거트를 싣고 내린다. 조회는 {@link PermissionQuery} 가 맡는다.
 */
public interface PermissionRepository {

    /** 수정 대상 애그리거트를 싣는다. */
    Optional<Permission> findById(Long permissionId);

    Permission save(Permission permission);

    void deleteById(Long permissionId);
}
