package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzPermissionEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzPermissionJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.authorization.Permission;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 권한 쓰기 어댑터 — 엔티티와 도메인 애그리거트를 오간다.
 */
@Component
@RequiredArgsConstructor
public class PermissionRepositoryAdapter implements PermissionRepository {

    private final AuthzPermissionJpaRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Permission> findById(Long permissionId) {
        return permissionRepository.findById(permissionId).map(PermissionRepositoryAdapter::toDomain);
    }

    /** 유일 제약이 서비스가 있을 때와 없을 때 둘로 나뉘어 있다(PostgreSQL 은 NULL 끼리 같다고 보지 않는다). */
    @Override
    @Transactional(readOnly = true)
    public boolean exists(Realm realm, String serviceId, String permissionCode) {
        return serviceId == null
                ? permissionRepository.existsByRealmAndServiceIdIsNullAndPermissionCode(realm, permissionCode)
                : permissionRepository.existsByRealmAndServiceIdAndPermissionCode(realm, serviceId, permissionCode);
    }

    @Override
    @Transactional
    public Permission save(Permission permission) {
        AuthzPermissionEntity entity = permission.getPermissionId() == null
                ? new AuthzPermissionEntity()
                : permissionRepository.findById(permission.getPermissionId())
                        .orElseThrow(() -> AuthorizationNotFoundException.permission(permission.getPermissionId()));
        entity.setRealm(permission.getRealm());
        entity.setServiceId(permission.getServiceId());
        entity.setPermissionCode(permission.getPermissionCode());
        entity.setPermissionName(permission.getPermissionName());
        entity.setCategory(permission.getCategory());
        entity.setDescription(permission.getDescription());
        entity.setActive(permission.isActive());
        return toDomain(permissionRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(Long permissionId) {
        permissionRepository.deleteById(permissionId);
    }

    private static Permission toDomain(AuthzPermissionEntity entity) {
        return Permission.restore(
                entity.getPermissionId(),
                entity.getRealm(),
                entity.getServiceId(),
                entity.getPermissionCode(),
                entity.getPermissionName(),
                entity.getCategory(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt());
    }
}
