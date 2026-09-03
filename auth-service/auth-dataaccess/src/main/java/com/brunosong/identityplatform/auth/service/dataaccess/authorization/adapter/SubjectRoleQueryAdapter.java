package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.SubjectRoleQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzRoleEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzSubjectRoleJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주체-역할 배정 조회 어댑터.
 *
 * <p>배정 조회는 역할의 기본 정보만 쓴다 — 권한 매핑까지 끌어오지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SubjectRoleQueryAdapter implements SubjectRoleQuery {

    private static final boolean ACTIVE = true;

    private final AuthzSubjectRoleJpaRepository subjectRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<String> permissionCodes(Realm realm, String subjectId) {
        return subjectRoleRepository.findPermissionCodes(realm, subjectId, ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleView> assignedRoles(Realm realm, String subjectId) {
        return subjectRoleRepository.findAssignedRoles(realm, subjectId).stream()
                .map(SubjectRoleQueryAdapter::toView)
                .toList();
    }

    private static RoleView toView(AuthzRoleEntity entity) {
        return new RoleView(entity.getRoleId(), entity.getRealm(), entity.getRoleCode(), entity.getRoleName(),
                entity.getDescription(), entity.isActive(), List.of());
    }
}
