package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleRepository;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.SubjectRoleRepository;
import com.brunosong.identityplatform.auth.service.domain.authorization.Role;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할 가산 부여 — 코드로 역할을 해석해 더한다.
 *
 * <p>없는 코드는 실패로 끝난다. 예전에는 경고만 남기고 넘어갔는데, 그러면 권한이 하나도 없는 계정이
 * 정상 가입된 것처럼 남는다 — 로그인은 되고 아무 화면도 못 여는 상태라 원인을 찾기 어렵다.
 *
 * <p>부여는 쓰기라 쓰기 포트만 쓴다. 조회 포트가 필요 없다는 것이 이 유스케이스의 성격이다.
 */
class SubjectRoleServiceTest {

    private FakeRoleRepository roleRepository;
    private FakeSubjectRoleRepository subjectRoleRepository;
    private SubjectRoleService service;

    @BeforeEach
    void setUp() {
        roleRepository = new FakeRoleRepository();
        subjectRoleRepository = new FakeSubjectRoleRepository();
        service = new SubjectRoleService(subjectRoleRepository, roleRepository);
    }

    @Test
    @DisplayName("역할 코드가 있으면 그 역할 id 를 주체에 더한다")
    void grantsResolvedRole() {
        roleRepository.add(Realm.PORTAL, "ROLE_USER", 10L);

        service.grant(Realm.PORTAL, "customer-1", "ROLE_USER");

        assertThat(subjectRoleRepository.added).containsExactly(10L);
    }

    @Test
    @DisplayName("없는 역할 코드는 실패한다")
    void unknownRoleCodeFails() {
        assertThatThrownBy(() -> service.grant(Realm.PORTAL, "customer-1", "ROLE_NOPE"))
                .isInstanceOf(AuthorizationNotFoundException.class);

        assertThat(subjectRoleRepository.added).isEmpty();
    }

    @Test
    @DisplayName("역할은 realm 안에서만 해석된다")
    void roleIsResolvedWithinRealm() {
        roleRepository.add(Realm.ADMIN, "ROLE_ADMIN", 20L);

        assertThatThrownBy(() -> service.grant(Realm.PORTAL, "customer-1", "ROLE_ADMIN"))
                .isInstanceOf(AuthorizationNotFoundException.class);

        assertThat(subjectRoleRepository.added).isEmpty();
    }

    @Test
    @DisplayName("같은 코드라도 realm 공통 역할과 서비스 역할은 서로 다른 역할이다")
    void realmRoleAndClientRoleAreDifferent() {
        roleRepository.add(Realm.PORTAL, "READ", 10L);
        roleRepository.add(Realm.PORTAL, "order-service", "READ", 11L);

        service.grant(Realm.PORTAL, "subject-1", null, "READ");
        service.grant(Realm.PORTAL, "subject-1", "order-service", "READ");

        // 이름이 같아도 다른 행이 붙는다 — 어휘가 서비스로 갈려 있어 부딪히지 않는다.
        assertThat(subjectRoleRepository.added).containsExactly(10L, 11L);
    }

    private static final class FakeRoleRepository implements RoleRepository {
        private final List<Role> roles = new ArrayList<>();

        void add(Realm realm, String roleCode, Long roleId) {
            add(realm, null, roleCode, roleId);
        }

        void add(Realm realm, String clientId, String roleCode, Long roleId) {
            roles.add(Role.restore(roleId, realm, clientId, roleCode, roleCode, null, true, List.of()));
        }

        @Override
        public Optional<Role> findByCode(Realm realm, String clientId, String roleCode) {
            return roles.stream()
                    .filter(role -> role.getRealm() == realm
                            && java.util.Objects.equals(role.getClientId(), clientId)
                            && role.getRoleCode().equals(roleCode))
                    .findFirst();
        }

        @Override
        public Optional<Role> findById(Long roleId) {
            return Optional.empty();
        }

        @Override
        public Role save(Role role) {
            return role;
        }

        @Override
        public void deleteById(Long roleId) {
        }

        @Override
        public void replacePermissions(Long roleId, Collection<Long> permissionIds) {
        }
    }

    private static final class FakeSubjectRoleRepository implements SubjectRoleRepository {
        final List<Long> added = new ArrayList<>();

        @Override
        public void addRole(Realm realm, String subjectId, Long roleId) {
            added.add(roleId);
        }

        @Override
        public void replaceRoles(Realm realm, String subjectId, List<Long> roleIds) {
        }
    }
}
