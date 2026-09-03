package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzRoleJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 역할 조회 어댑터 — 엔티티를 읽기 모델로 바로 투영한다. 도메인 애그리거트를 거치지 않는다.
 *
 * <p>조건 검색은 {@link RoleSearchQueryAdapter} 가 맡는다.
 */
@Component
@RequiredArgsConstructor
public class RoleQueryAdapter implements RoleQuery {

    private final AuthzRoleJpaRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RoleView> listActive(Realm realm) {
        return roleRepository.findByRealmAndActiveIsTrueOrderByRoleId(realm).stream()
                .map(RoleViewMapper::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleView> findById(Long roleId) {
        return roleRepository.findById(roleId).map(RoleViewMapper::toView);
    }
}
