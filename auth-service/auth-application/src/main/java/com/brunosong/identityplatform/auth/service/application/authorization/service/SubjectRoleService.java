package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.AssignSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.GrantRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleRepository;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.SubjectRoleRepository;
import com.brunosong.identityplatform.auth.service.domain.authorization.Role;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 주체에게 붙은 역할에 관한 유스케이스 구현. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SubjectRoleService implements GrantRoleUseCase, AssignSubjectRolesUseCase {

    private final SubjectRoleRepository subjectRoleRepository;
    private final RoleRepository roleRepository;

    /**
     * 역할이 없으면 실패한다. 예전에는 경고만 남기고 넘어갔는데, 그러면 권한이 하나도 없는 계정이
     * 정상 가입된 것처럼 남는다 — 로그인은 되고 아무 화면도 못 여는 상태라 원인을 찾기 어렵다.
     */
    @Override
    @Transactional
    public void grant(Realm realm, String subjectId, String clientId, String roleCode) {
        Role role = roleRepository.findByCode(realm, clientId, roleCode)
                .orElseThrow(() -> new AuthorizationNotFoundException(
                        "역할을 찾을 수 없습니다: realm=" + realm + ", clientId=" + clientId
                                + ", roleCode=" + roleCode));
        subjectRoleRepository.addRole(realm, subjectId, role.getRoleId());
        log.info("역할 가산 부여: realm={}, subjectId={}, clientId={}, roleCode={}",
                realm, subjectId, clientId, roleCode);
    }

    @Override
    @Transactional
    public void assign(Realm realm, String subjectId, List<Long> roleIds) {
        subjectRoleRepository.replaceRoles(realm, subjectId, roleIds);
    }
}
