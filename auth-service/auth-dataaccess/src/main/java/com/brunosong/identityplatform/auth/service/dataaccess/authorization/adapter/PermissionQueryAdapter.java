package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzPermissionJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 권한 조회 어댑터 — 엔티티를 읽기 모델로 바로 투영한다.
 *
 * <p>조건 검색은 {@link PermissionSearchQueryAdapter} 가 맡는다.
 */
@Component
@RequiredArgsConstructor
public class PermissionQueryAdapter implements PermissionQuery {

    private final AuthzPermissionJpaRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionView> listActive(Realm realm) {
        return permissionRepository.findByRealmAndActiveIsTrueOrderByCategoryAscPermissionCodeAsc(realm).stream()
                .map(PermissionViewMapper::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PermissionView> findById(Long permissionId) {
        return permissionRepository.findById(permissionId).map(PermissionViewMapper::toView);
    }
}
