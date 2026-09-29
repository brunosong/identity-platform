package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.RoleAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreateRoleCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.RoleRepository;
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
 * 역할 만들기 — 역할 코드는 realm 안에서 하나다.
 */
class RoleServiceTest {

    private FakeRoleRepository roles;
    private RoleService service;

    @BeforeEach
    void setUp() {
        roles = new FakeRoleRepository();
        service = new RoleService(roles, null, null, null);
    }

    @Test
    @DisplayName("같은 realm 에 같은 코드가 있으면 만들지 않는다")
    void duplicateCodeFails() {
        service.create(new CreateRoleCommand(Realm.ADMIN, "AUDITOR", "감사", null));

        assertThatThrownBy(() -> service.create(new CreateRoleCommand(Realm.ADMIN, " AUDITOR ", "감사 2", null)))
                .isInstanceOf(RoleAlreadyExistsException.class)
                .hasMessageContaining("AUDITOR");
        assertThat(roles.saved).hasSize(1);
    }

    @Test
    @DisplayName("realm 이 다르면 같은 코드를 만들 수 있다")
    void sameCodeInOtherRealm() {
        service.create(new CreateRoleCommand(Realm.ADMIN, "AUDITOR", "감사", null));
        service.create(new CreateRoleCommand(Realm.PORTAL, "AUDITOR", "감사", null));

        assertThat(roles.saved).hasSize(2);
    }

    static class FakeRoleRepository implements RoleRepository {

        final List<Role> saved = new ArrayList<>();

        @Override
        public Optional<Role> findById(Long roleId) {
            return Optional.empty();
        }

        @Override
        public Optional<Role> findByCode(Realm realm, String roleCode) {
            return saved.stream()
                    .filter(r -> r.getRealm() == realm && r.getRoleCode().equals(roleCode))
                    .findFirst();
        }

        @Override
        public Role save(Role role) {
            Role stored = Role.restore((long) saved.size() + 1, role.getRealm(), role.getRoleCode(),
                    role.getRoleName(), role.getDescription(), role.isActive(), role.getPermissionIds());
            saved.add(stored);
            return stored;
        }

        @Override
        public void deleteById(Long roleId) {
        }

        @Override
        public void replacePermissions(Long roleId, Collection<Long> permissionIds) {
        }
    }
}
