package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.exception.RoleAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.CreateRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.DeleteRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.RenameRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreateRoleCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.RenameRoleCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleSearchQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.SubjectRoleQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleRepository;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.authorization.Role;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 역할에 관한 유스케이스 구현.
 *
 * <p>클래스는 의도별로 쪼개지 않고 다루는 개념으로 묶는다 — 의도는 인터페이스가 나타내고,
 * 호출자는 자기가 쓸 의도만 주입받는다. 읽기/쓰기 구분은 트랜잭션 설정과 포트 선택이 전부다.
 *
 * <p>쓰기는 {@link RoleRepository}(애그리거트)로, 읽기는 {@link RoleQuery}(읽기 모델)로 간다.
 *
 * <p>조건으로 거르는 조회는 {@link RoleSearchQuery} 로 간다 — 검색은 조건이 늘고 저장소가
 * 갈릴 여지가 커서 포트를 따로 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService implements CreateRoleUseCase, RenameRoleUseCase, DeleteRoleUseCase,
        FindRolesUseCase {

    private final RoleRepository roleRepository;
    private final RoleQuery roleQuery;
    private final RoleSearchQuery roleSearchQuery;
    private final SubjectRoleQuery subjectRoleQuery;

    @Override
    @Transactional
    public RoleView create(CreateRoleCommand command) {
        Role role = Role.create(command.realm(), command.roleCode(), command.roleName(), command.description());
        if (roleRepository.findByCode(role.getRealm(), role.getRoleCode()).isPresent()) {
            throw new RoleAlreadyExistsException(role.getRealm(), role.getRoleCode());
        }
        return RoleView.from(roleRepository.save(role));
    }

    /** 저장본을 읽어 표시 정보만 바꾼다 — realm/roleCode 는 도메인이 불변으로 잡고 있다. */
    @Override
    @Transactional
    public RoleView rename(RenameRoleCommand command) {
        Role role = roleRepository.findById(command.roleId())
                .orElseThrow(() -> AuthorizationNotFoundException.role(command.roleId()));
        role.describeAs(command.roleName(), command.description());
        return RoleView.from(roleRepository.save(role));
    }

    @Override
    @Transactional
    public void delete(Long roleId) {
        roleRepository.deleteById(roleId);
    }

    @Override
    public List<RoleView> of(Realm realm, String keyword) {
        return roleSearchQuery.search(realm, keyword);
    }

    @Override
    public Optional<RoleView> byId(Long roleId) {
        return roleQuery.findById(roleId);
    }

    @Override
    public List<RoleView> ofSubject(Realm realm, String subjectId) {
        return subjectRoleQuery.assignedRoles(realm, subjectId);
    }
}
