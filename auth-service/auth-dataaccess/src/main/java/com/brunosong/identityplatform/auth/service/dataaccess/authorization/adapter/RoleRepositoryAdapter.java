package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzPermissionEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzRoleEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzPermissionJpaRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzRoleJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.authorization.Role;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 역할 쓰기 어댑터 — 엔티티와 도메인 애그리거트를 오간다.
 *
 * <p>수정 저장은 기존 엔티티를 로드해 기본 필드만 갱신한다. 권한 매핑은 보존하고
 * {@link #replacePermissions} 로만 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepository {

    private final AuthzRoleJpaRepository roleRepository;
    private final AuthzPermissionJpaRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findById(Long roleId) {
        return roleRepository.findById(roleId).map(RoleRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByCode(Realm realm, String roleCode) {
        return roleRepository.findByRealmAndRoleCode(realm, roleCode).map(RoleRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public Role save(Role role) {
        AuthzRoleEntity entity = role.getRoleId() == null
                ? new AuthzRoleEntity()
                : roleRepository.findById(role.getRoleId())
                        .orElseThrow(() -> AuthorizationNotFoundException.role(role.getRoleId()));
        entity.setRealm(role.getRealm());
        entity.setRoleCode(role.getRoleCode());
        entity.setRoleName(role.getRoleName());
        entity.setDescription(role.getDescription());
        entity.setActive(role.isActive());
        return toDomain(roleRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(Long roleId) {
        roleRepository.deleteById(roleId);
    }

    @Override
    @Transactional
    public void replacePermissions(Long roleId, Collection<Long> permissionIds) {
        AuthzRoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthorizationNotFoundException.role(roleId));
        Set<AuthzPermissionEntity> permissions = new LinkedHashSet<>();
        for (Long permissionId : permissionIds) {
            permissions.add(permissionRepository.findById(permissionId)
                    .orElseThrow(() -> AuthorizationNotFoundException.permission(permissionId)));
        }
        role.setPermissions(permissions);
        roleRepository.save(role);
    }

    private static Role toDomain(AuthzRoleEntity entity) {
        return Role.restore(
                entity.getRoleId(),
                entity.getRealm(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getPermissions().stream()
                        .map(AuthzPermissionEntity::getPermissionId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
}
