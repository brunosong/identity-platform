package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzPermissionEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzRoleEntity;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlAccess;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlRule;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인가 저장소(주체-역할 / URL 접근 규칙) 영속 경로 테스트.
 *
 * <p>보는 지점은 realm 스코프와 조인이다. 한 저장소를 admin(EMPLOYEE)·portal(CUSTOMER)이 공유하므로
 * realm 이 새면 다른 호스트의 권한이 그대로 흘러간다. 권한 코드는 주체→역할→권한 2단 조인에
 * 활성 필터(use_yn)까지 걸리는 자리라 조인이 틀리면 인가가 조용히 넓어진다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SubjectRoleRepositoryAdapter.class, SubjectRoleQueryAdapter.class,
        UrlAccessRepositoryAdapter.class, UrlAccessQueryAdapter.class})
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
    private UrlAccessRepositoryAdapter urlAccessRepository;

    @Autowired
    private UrlAccessQueryAdapter urlAccessQuery;

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

            assertThat(activeRoleCodes(Realm.ADMIN, "EMP-1"))
                    .containsExactly("ROLE_MANAGER");
            assertThat(subjectRoleQuery.permissionCodes(Realm.ADMIN, "EMP-1"))
                    .containsExactlyInAnyOrder("RESUME_READ", "RESUME_WRITE");
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

            assertThat(activeRoleCodes(Realm.ADMIN, "EMP-2"))
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

            assertThat(activeRoleCodes(Realm.ADMIN, "SAME-ID")).containsExactly("ROLE_EMP");
            assertThat(activeRoleCodes(Realm.PORTAL, "SAME-ID")).isEmpty();
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

            assertThat(activeRoleCodes(Realm.ADMIN, "EMP-3")).containsExactly("ROLE_B");
            assertThat(activeRoleCodes(Realm.ADMIN, "EMP-4")).containsExactly("ROLE_A");
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

    @Nested
    @DisplayName("URL 접근 규칙")
    class UrlAccessRules {

        @Test
        @DisplayName("규칙을 저장하고 realm 활성 목록에서 정렬 순서대로 읽는다")
        void activeRulesSortedWithinRealm() {
            saveRule(Realm.ADMIN, "/api/admin/**", "ALL", 2, true);
            saveRule(Realm.ADMIN, "/api/admin/resumes/**", "GET", 1, true);
            saveRule(Realm.ADMIN, "/api/admin/legacy/**", "ALL", 3, false);
            saveRule(Realm.PORTAL, "/api/resumes/**", "ALL", 1, true);
            flushClear();

            assertThat(urlAccessQuery.listActive(Realm.ADMIN))
                    .extracting(UrlAccessView::urlPattern)
                    .containsExactly("/api/admin/resumes/**", "/api/admin/**");
            assertThat(urlAccessQuery.listActive(Realm.PORTAL))
                    .extracting(UrlAccessView::urlPattern)
                    .containsExactly("/api/resumes/**");
        }

        @Test
        @DisplayName("기본 필드 수정은 권한 매핑을 보존한다")
        void saveBasicsKeepsPermissionMapping() {
            AuthzPermissionEntity perm = permission(Realm.ADMIN, "ADMIN_READ", true);
            UrlAccess rule = saveRule(Realm.ADMIN, "/api/admin/keep/**", "ALL", 1, true);
            urlAccessRepository.addPermission(rule.getUrlAccessId(), perm.getPermissionId());
            flushClear();

            UrlAccess reloaded = urlAccessRepository.findById(rule.getUrlAccessId()).orElseThrow();
            reloaded.describeAs("설명만 바꾼다", reloaded.getSortOrder());
            urlAccessRepository.saveBasics(reloaded);
            flushClear();

            UrlAccess after = urlAccessRepository.findById(rule.getUrlAccessId()).orElseThrow();
            assertThat(after.getDescription()).isEqualTo("설명만 바꾼다");
            assertThat(after.getPermissionCodes()).containsExactly("ADMIN_READ");
        }

        @Test
        @DisplayName("권한 매핑을 붙이고 뗀다")
        void addAndRemovePermission() {
            AuthzPermissionEntity read = permission(Realm.ADMIN, "MAP_READ", true);
            AuthzPermissionEntity write = permission(Realm.ADMIN, "MAP_WRITE", true);
            UrlAccess rule = saveRule(Realm.ADMIN, "/api/admin/map/**", "ALL", 1, true);

            urlAccessRepository.addPermission(rule.getUrlAccessId(), read.getPermissionId());
            urlAccessRepository.addPermission(rule.getUrlAccessId(), write.getPermissionId());
            flushClear();
            assertThat(urlAccessRepository.findById(rule.getUrlAccessId()).orElseThrow().getPermissionCodes())
                    .containsExactly("MAP_READ", "MAP_WRITE");

            urlAccessRepository.removePermission(rule.getUrlAccessId(), read.getPermissionId());
            flushClear();
            assertThat(urlAccessRepository.findById(rule.getUrlAccessId()).orElseThrow().getPermissionCodes())
                    .containsExactly("MAP_WRITE");
        }

        @Test
        @DisplayName("없는 규칙·권한에 매핑하면 거부한다")
        void mappingMissingRowsRejected() {
            UrlAccess rule = saveRule(Realm.ADMIN, "/api/admin/reject/**", "ALL", 1, true);
            flushClear();

            assertThatThrownBy(() -> urlAccessRepository.addPermission(999_999L, 1L))
                    .isInstanceOf(AuthorizationNotFoundException.class);
            assertThatThrownBy(() -> urlAccessRepository.addPermission(rule.getUrlAccessId(), 999_999L))
                    .isInstanceOf(AuthorizationNotFoundException.class);
        }

        @Test
        @DisplayName("인가 캐시용 규칙은 패턴+메서드로 권한 코드를 모아 준다")
        void activeRulesGroupPermissionCodes() {
            AuthzPermissionEntity read = permission(Realm.ADMIN, "CACHE_READ", true);
            AuthzPermissionEntity write = permission(Realm.ADMIN, "CACHE_WRITE", true);
            UrlAccess both = saveRule(Realm.ADMIN, "/api/admin/cache/**", "ALL", 1, true);
            urlAccessRepository.addPermission(both.getUrlAccessId(), read.getPermissionId());
            urlAccessRepository.addPermission(both.getUrlAccessId(), write.getPermissionId());
            // 매핑 없는 규칙은 조인이 걸러 캐시 규칙에 나오지 않는다.
            saveRule(Realm.ADMIN, "/api/admin/unmapped/**", "ALL", 2, true);
            flushClear();

            List<UrlRule> rules = urlAccessQuery.activeRules(Realm.ADMIN);

            assertThat(rules).singleElement().satisfies(r -> {
                assertThat(r.urlPattern()).isEqualTo("/api/admin/cache/**");
                assertThat(r.httpMethod().value()).isEqualTo("ALL");
                assertThat(r.permissionCodes()).containsExactlyInAnyOrder("CACHE_READ", "CACHE_WRITE");
            });
        }

        @Test
        @DisplayName("비활성 규칙은 인가 캐시에서 빠진다")
        void inactiveRuleExcludedFromCache() {
            AuthzPermissionEntity perm = permission(Realm.PORTAL, "OFF_PERM", true);
            UrlAccess off = saveRule(Realm.PORTAL, "/api/off/**", "ALL", 1, false);
            urlAccessRepository.addPermission(off.getUrlAccessId(), perm.getPermissionId());
            flushClear();

            assertThat(urlAccessQuery.activeRules(Realm.PORTAL)).isEmpty();
        }

        @Test
        @DisplayName("권한 기준 역방향 조회는 활성 규칙만 준다")
        void findByPermissionIdSkipsInactive() {
            AuthzPermissionEntity perm = permission(Realm.ADMIN, "REV_PERM", true);
            UrlAccess live = saveRule(Realm.ADMIN, "/api/admin/rev-live/**", "ALL", 1, true);
            UrlAccess off = saveRule(Realm.ADMIN, "/api/admin/rev-off/**", "ALL", 2, false);
            urlAccessRepository.addPermission(live.getUrlAccessId(), perm.getPermissionId());
            urlAccessRepository.addPermission(off.getUrlAccessId(), perm.getPermissionId());
            flushClear();

            assertThat(urlAccessRepository.findByPermissionId(perm.getPermissionId()))
                    .extracting(UrlAccess::getUrlPattern)
                    .containsExactly("/api/admin/rev-live/**");
        }

        @Test
        @DisplayName("패턴+메서드 조회는 realm 안에서만 찾는다")
        void findByPatternScopedToRealm() {
            saveRule(Realm.ADMIN, "/api/shared/**", "GET", 1, true);
            flushClear();

            assertThat(urlAccessRepository.findByPatternAndMethod(Realm.ADMIN, "/api/shared/**", "GET"))
                    .hasSize(1);
            assertThat(urlAccessRepository.findByPatternAndMethod(Realm.PORTAL, "/api/shared/**", "GET"))
                    .isEmpty();
            assertThat(urlAccessRepository.findByPatternAndMethod(Realm.ADMIN, "/api/shared/**", "POST"))
                    .isEmpty();
        }

        @Test
        @DisplayName("규칙을 지우면 활성 목록에서 사라진다")
        void deleteRule() {
            UrlAccess rule = saveRule(Realm.PORTAL, "/api/delete-me/**", "ALL", 1, true);
            flushClear();

            urlAccessRepository.deleteById(rule.getUrlAccessId());
            flushClear();

            assertThat(urlAccessRepository.findById(rule.getUrlAccessId())).isEmpty();
        }

        private UrlAccess saveRule(Realm realm, String pattern, String method, int sortOrder, boolean active) {
            UrlAccess rule = UrlAccess.create(realm, pattern, method, null, sortOrder);
            if (!active) {
                rule.deactivate();
            }
            return urlAccessRepository.saveBasics(rule);
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

    /**
     * 배정된 역할 중 활성인 것의 코드. 예전에는 저장소 포트에 이 조회가 있었는데, 실제로 부르는
     * 곳이 없어 포트에서 걷어내고 검증만 여기 남겼다 — 활성 필터가 도는지는 계속 봐야 한다.
     */
    private List<String> activeRoleCodes(Realm realm, String subjectId) {
        return subjectRoleQuery.assignedRoles(realm, subjectId).stream()
                .filter(RoleView::active)
                .map(RoleView::roleCode)
                .toList();
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }
}
