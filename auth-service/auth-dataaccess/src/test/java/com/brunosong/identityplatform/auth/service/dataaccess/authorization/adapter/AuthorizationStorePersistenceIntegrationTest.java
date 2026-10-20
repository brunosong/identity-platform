package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzPermissionEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzRoleEntity;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인가 저장소(주체-역할) 영속 경로 테스트.
 *
 * <p>보는 지점은 realm 스코프와 조인이다. 한 저장소를 admin(EMPLOYEE)·portal(CUSTOMER)이 공유하므로
 * realm 이 새면 다른 호스트의 권한이 그대로 흘러간다. 권한 코드는 주체→역할→권한 2단 조인에
 * 활성 필터(use_yn)까지 걸리는 자리라 조인이 틀리면 인가가 조용히 넓어진다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SubjectRoleRepositoryAdapter.class, SubjectRoleQueryAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class AuthorizationStorePersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SubjectRoleRepositoryAdapter subjectRoleRepository;

    @Autowired
    private SubjectRoleQueryAdapter subjectRoleQuery;

    @Autowired
    private EntityManager em;

    @Nested
    @DisplayName("주체-역할 배정")
    class SubjectRoles {

        @Test
        @DisplayName("역할을 배정하면 역할 코드와 권한 코드가 조인으로 나온다")
        void rolePermissionJoin() {
            AuthzPermissionEntity read = permission(Realm.ADMIN, "RESUME_READ", true);
            AuthzPermissionEntity write = permission(Realm.ADMIN, "RESUME_WRITE", true);
            Long manager = role(Realm.ADMIN, "ROLE_MANAGER", true, read, write).getRoleId();
            subjectRoleRepository.addRole(Realm.ADMIN, "EMP-1", manager);
            flushClear();

            assertThat(subjectRoleQuery.roleCodes(Realm.ADMIN, "EMP-1"))
                    .containsExactly("ROLE_MANAGER");
            assertThat(subjectRoleQuery.permissionCodes(Realm.ADMIN, "EMP-1"))
                    .containsExactlyInAnyOrder("RESUME_READ", "RESUME_WRITE");
        }

        @Test
        @DisplayName("권한이 서비스별로 묶여 나오고, 서비스가 없는 권한은 빠진다")
        void permissionsGroupedByService() {
            service("order-service");
            service("customer-service");
            AuthzPermissionEntity orderRead = permission(Realm.PORTAL, "order-service", "ORDER_READ", true);
            AuthzPermissionEntity orderWrite = permission(Realm.PORTAL, "order-service", "ORDER_WRITE", true);
            AuthzPermissionEntity profileRead = permission(Realm.PORTAL, "customer-service", "PROFILE_READ", true);
            AuthzPermissionEntity console = permission(Realm.PORTAL, "CONSOLE_ONLY", true);
            Long customer = role(Realm.PORTAL, "CUSTOMER", true, orderWrite, orderRead, profileRead, console)
                    .getRoleId();
            // 두 역할이 같은 권한을 줘도 한 번만 나와야 한다.
            Long vip = role(Realm.PORTAL, "VIP", true, orderRead).getRoleId();
            subjectRoleRepository.addRole(Realm.PORTAL, "CUST-9", customer);
            subjectRoleRepository.addRole(Realm.PORTAL, "CUST-9", vip);
            flushClear();

            Map<String, List<String>> byService = subjectRoleQuery.permissionsByService(Realm.PORTAL, "CUST-9");

            assertThat(byService).containsExactly(
                    Map.entry("customer-service", List.of("PROFILE_READ")),
                    Map.entry("order-service", List.of("ORDER_READ", "ORDER_WRITE")));
        }

        @Test
        @DisplayName("다른 realm 의 역할은 배정되지 않는다 — DB 가 막는다")
        void crossRealmAssignmentIsRejected() {
            Long adminRole = role(Realm.ADMIN, "ROLE_ADMIN_ONLY", true).getRoleId();

            // 포털 주체에게 어드민 역할을 붙이려 한다. 코드가 realm 으로 먼저 찾아서 막고 있지만,
            // roleId 를 그대로 받는 경로(관리 API 의 역할 일괄 배정)가 있으므로 DB 가 붙들어야 한다.
            assertThatThrownBy(() -> {
                subjectRoleRepository.addRole(Realm.PORTAL, "CUSTOMER-1", adminRole);
                flushClear();
            }).hasMessageContaining("fk_authz_subject_role_role");
        }

        @Test
        @DisplayName("비활성 역할과 비활성 권한은 권한 코드에서 빠진다")
        void inactiveRowsExcluded() {
            AuthzPermissionEntity active = permission(Realm.ADMIN, "ACTIVE_PERM", true);
            AuthzPermissionEntity retired = permission(Realm.ADMIN, "RETIRED_PERM", false);
            Long liveRole = role(Realm.ADMIN, "ROLE_LIVE", true, active, retired).getRoleId();
            Long deadRole = role(Realm.ADMIN, "ROLE_DEAD", false, active).getRoleId();
            subjectRoleRepository.addRole(Realm.ADMIN, "EMP-2", liveRole);
            subjectRoleRepository.addRole(Realm.ADMIN, "EMP-2", deadRole);
            flushClear();

            assertThat(subjectRoleQuery.roleCodes(Realm.ADMIN, "EMP-2"))
                    .containsExactly("ROLE_LIVE");
            assertThat(subjectRoleQuery.permissionCodes(Realm.ADMIN, "EMP-2"))
                    .containsExactly("ACTIVE_PERM");
        }

        @Test
        @DisplayName("realm 이 다르면 같은 주체 식별자라도 배정이 보이지 않는다")
        void realmScoped() {
            Long employeeRole = role(Realm.ADMIN, "ROLE_EMP", true).getRoleId();
            subjectRoleRepository.addRole(Realm.ADMIN, "SAME-ID", employeeRole);
            flushClear();

            assertThat(subjectRoleQuery.roleCodes(Realm.ADMIN, "SAME-ID")).containsExactly("ROLE_EMP");
            assertThat(subjectRoleQuery.roleCodes(Realm.PORTAL, "SAME-ID")).isEmpty();
            assertThat(subjectRoleQuery.assignedRoles(Realm.PORTAL, "SAME-ID")).isEmpty();
        }

        @Test
        @DisplayName("같은 역할을 두 번 배정해도 한 번만 남는다")
        void addRoleIsIdempotent() {
            Long roleId = role(Realm.PORTAL, "ROLE_MEMBER", true).getRoleId();
            subjectRoleRepository.addRole(Realm.PORTAL, "CUST-1", roleId);
            flushClear();
            subjectRoleRepository.addRole(Realm.PORTAL, "CUST-1", roleId);
            flushClear();

            assertThat(subjectRoleQuery.assignedRoles(Realm.PORTAL, "CUST-1")).hasSize(1);
        }

        @Test
        @DisplayName("배정된 역할이 도메인 모델로 복원된다")
        void assignedRolesMapped() {
            Long roleId = role(Realm.PORTAL, "ROLE_MEMBER", true).getRoleId();
            subjectRoleRepository.addRole(Realm.PORTAL, "CUST-2", roleId);
            flushClear();

            List<RoleView> roles = subjectRoleQuery.assignedRoles(Realm.PORTAL, "CUST-2");

            assertThat(roles).singleElement().satisfies(role -> {
                assertThat(role.roleId()).isEqualTo(roleId);
                assertThat(role.realm()).isEqualTo(Realm.PORTAL);
                assertThat(role.roleCode()).isEqualTo("ROLE_MEMBER");
                assertThat(role.active()).isTrue();
            });
        }

        @Test
        @DisplayName("역할 교체는 그 주체의 배정만 갈아끼운다")
        void replaceRolesScopedToSubject() {
            Long first = role(Realm.ADMIN, "ROLE_A", true).getRoleId();
            Long second = role(Realm.ADMIN, "ROLE_B", true).getRoleId();
            subjectRoleRepository.addRole(Realm.ADMIN, "EMP-3", first);
            subjectRoleRepository.addRole(Realm.ADMIN, "EMP-4", first);
            flushClear();

            subjectRoleRepository.replaceRoles(Realm.ADMIN, "EMP-3", List.of(second));
            flushClear();

            assertThat(subjectRoleQuery.roleCodes(Realm.ADMIN, "EMP-3")).containsExactly("ROLE_B");
            assertThat(subjectRoleQuery.roleCodes(Realm.ADMIN, "EMP-4")).containsExactly("ROLE_A");
        }

        @Test
        @DisplayName("빈 목록으로 교체하면 배정이 모두 사라진다")
        void replaceWithEmptyClearsAll() {
            Long roleId = role(Realm.ADMIN, "ROLE_C", true).getRoleId();
            subjectRoleRepository.addRole(Realm.ADMIN, "EMP-5", roleId);
            flushClear();

            subjectRoleRepository.replaceRoles(Realm.ADMIN, "EMP-5", List.of());
            flushClear();

            assertThat(subjectRoleQuery.assignedRoles(Realm.ADMIN, "EMP-5")).isEmpty();
        }
    }

    private AuthzPermissionEntity permission(Realm realm, String code, boolean active) {
        AuthzPermissionEntity e = new AuthzPermissionEntity();
        e.setRealm(realm);
        e.setPermissionCode(code);
        e.setPermissionName(code);
        e.setCategory("TEST");
        e.setActive(active);
        em.persist(e);
        return e;
    }

    private AuthzPermissionEntity permission(Realm realm, String serviceId, String code, boolean active) {
        AuthzPermissionEntity e = permission(realm, code, active);
        e.setServiceId(serviceId);
        return e;
    }

    /** 권한의 service_id 가 가리킬 서비스. 엔티티가 없어 SQL 로 넣는다. */
    private void service(String serviceId) {
        em.createNativeQuery("INSERT INTO authz_service (service_id, realm, system_id, service_name) "
                        + "VALUES (?1, 'PORTAL', 'shop', ?1)")
                .setParameter(1, serviceId)
                .executeUpdate();
    }

    private AuthzRoleEntity role(Realm realm, String code, boolean active, AuthzPermissionEntity... permissions) {
        AuthzRoleEntity e = new AuthzRoleEntity();
        e.setRealm(realm);
        e.setRoleCode(code);
        e.setRoleName(code);
        e.setActive(active);
        e.getPermissions().addAll(List.of(permissions));
        em.persist(e);
        return e;
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }
}
