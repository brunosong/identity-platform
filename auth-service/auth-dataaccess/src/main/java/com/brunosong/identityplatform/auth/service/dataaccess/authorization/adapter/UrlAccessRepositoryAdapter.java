package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.UrlAccessRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzPermissionEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzUrlAccessEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzPermissionJpaRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzUrlAccessJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlAccess;
import com.brunosong.identityplatform.auth.service.domain.authorization.valueobject.HttpMethodPattern;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * URL 접근 규칙 쓰기 어댑터 — 엔티티와 도메인 애그리거트를 오간다.
 * saveBasics 는 기본 필드만 갱신하고 권한 매핑은 보존한다.
 */
@Component
@RequiredArgsConstructor
public class UrlAccessRepositoryAdapter implements UrlAccessRepository {

    private static final boolean ACTIVE = true;

    private final AuthzUrlAccessJpaRepository urlAccessRepository;
    private final AuthzPermissionJpaRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UrlAccess> findById(Long urlAccessId) {
        return urlAccessRepository.findById(urlAccessId).map(UrlAccessRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UrlAccess> findByPatternAndMethod(Realm realm, String urlPattern, String httpMethod) {
        return urlAccessRepository
                .findByRealmAndUrlPatternAndHttpMethod(realm, urlPattern, HttpMethodPattern.of(httpMethod).value())
                .stream().map(UrlAccessRepositoryAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UrlAccess> findByPermissionId(Long permissionId) {
        return urlAccessRepository.findByPermissionId(permissionId, ACTIVE).stream()
                .map(UrlAccessRepositoryAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public UrlAccess saveBasics(UrlAccess urlAccess) {
        AuthzUrlAccessEntity entity = urlAccess.getUrlAccessId() == null
                ? new AuthzUrlAccessEntity()
                : urlAccessRepository.findById(urlAccess.getUrlAccessId())
                        .orElseThrow(() -> AuthorizationNotFoundException.urlAccess(urlAccess.getUrlAccessId()));
        entity.setRealm(urlAccess.getRealm());
        entity.setUrlPattern(urlAccess.getUrlPattern());
        entity.setHttpMethod(urlAccess.getHttpMethod().value());
        entity.setDescription(urlAccess.getDescription());
        entity.setSortOrder(urlAccess.getSortOrder());
        entity.setActive(urlAccess.isActive());
        return toDomain(urlAccessRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(Long urlAccessId) {
        urlAccessRepository.deleteById(urlAccessId);
    }

    @Override
    @Transactional
    public void addPermission(Long urlAccessId, Long permissionId) {
        AuthzUrlAccessEntity urlAccess = urlAccessRepository.findById(urlAccessId)
                .orElseThrow(() -> AuthorizationNotFoundException.urlAccess(urlAccessId));
        AuthzPermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> AuthorizationNotFoundException.permission(permissionId));
        urlAccess.getPermissions().add(permission);
        urlAccessRepository.save(urlAccess);
    }

    @Override
    @Transactional
    public void removePermission(Long urlAccessId, Long permissionId) {
        AuthzUrlAccessEntity urlAccess = urlAccessRepository.findById(urlAccessId)
                .orElseThrow(() -> AuthorizationNotFoundException.urlAccess(urlAccessId));
        urlAccess.getPermissions().removeIf(permission -> permission.getPermissionId().equals(permissionId));
        urlAccessRepository.save(urlAccess);
    }

    private static UrlAccess toDomain(AuthzUrlAccessEntity entity) {
        Set<String> permissionCodes = entity.getPermissions().stream()
                .map(AuthzPermissionEntity::getPermissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return UrlAccess.restore(
                entity.getUrlAccessId(),
                entity.getRealm(),
                entity.getUrlPattern(),
                entity.getHttpMethod(),
                entity.getDescription(),
                entity.isActive(),
                entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                entity.getCreatedAt(),
                permissionCodes.stream().sorted().toList());
    }
}
