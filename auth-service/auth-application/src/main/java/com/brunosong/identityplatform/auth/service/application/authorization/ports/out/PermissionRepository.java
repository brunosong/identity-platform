package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.domain.authorization.Permission;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Optional;

/**
 * 권한 쓰기 포트(SPI) — 애그리거트를 싣고 내린다. 조회는 {@link PermissionQuery} 가 맡는다.
 */
public interface PermissionRepository {

    /** 수정 대상 애그리거트를 싣는다. */
    Optional<Permission> findById(Long permissionId);

    /**
     * 같은 자리에 같은 코드가 있나. 자리는 realm 과 서비스다. 서비스가 null 이면 어드민 콘솔 자신의 권한끼리 본다.
     */
    boolean exists(Realm realm, String serviceId, String permissionCode);

    Permission save(Permission permission);

    void deleteById(Long permissionId);
}
