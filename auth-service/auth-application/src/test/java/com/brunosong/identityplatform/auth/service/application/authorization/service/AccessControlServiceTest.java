package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.SubjectRoleQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.UrlAccessQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlResourceView;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlRule;
import com.brunosong.identityplatform.auth.service.domain.authorization.valueobject.HttpMethodPattern;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 접근제어 엔진. 패턴·메서드 매칭, 권한 OR 판정, realm 별 기본 정책(fail-open/closed)을 본다.
 */
class AccessControlServiceTest {

    private static final long NO_EXPIRY = 0L;
    private static final List<String> PROTECTED_PREFIXES = List.of("/page/", "/api/");

    private FakeUrlAccessQuery urlAccessQuery;
    private AccessControlService service;

    @BeforeEach
    void setUp() {
        urlAccessQuery = new FakeUrlAccessQuery();
        service = new AccessControlService(urlAccessQuery, new FakeSubjectRoleQuery(), NO_EXPIRY, PROTECTED_PREFIXES);
    }

    private static UrlRule rule(String pattern, String method, Set<String> codes) {
        return new UrlRule(pattern, HttpMethodPattern.of(method), codes);
    }

    @Test
    @DisplayName("매칭 규칙의 권한을 하나라도 가지면 허용한다(OR)")
    void anyOfMappedPermissionsAllows() {
        urlAccessQuery.rules(Realm.ADMIN, rule("/api/resumes/**", "ALL", Set.of("RESUME_READ", "RESUME_ADMIN")));

        assertThat(service.hasAccess(Realm.ADMIN, "/api/resumes/1", "GET", Set.of("RESUME_ADMIN"))).isTrue();
    }

    @Test
    @DisplayName("매칭 규칙이 있는데 권한이 없으면 거부한다")
    void matchedButUnauthorizedIsDenied() {
        urlAccessQuery.rules(Realm.ADMIN, rule("/api/resumes/**", "ALL", Set.of("RESUME_READ")));

        assertThat(service.hasAccess(Realm.ADMIN, "/api/resumes/1", "GET", Set.of("OTHER"))).isFalse();
    }

    @Test
    @DisplayName("메서드가 지정된 규칙은 다른 메서드 요청에 적용되지 않는다")
    void methodScopedRuleIgnoresOtherMethods() {
        urlAccessQuery.rules(Realm.PORTAL, rule("/api/resumes/**", "POST", Set.of("RESUME_WRITE")));

        assertThat(service.hasAccess(Realm.PORTAL, "/api/resumes/1", "POST", Set.of())).isFalse();
        // CUSTOMER 는 fail-open — 매칭 규칙이 없는 GET 은 통과
        assertThat(service.hasAccess(Realm.PORTAL, "/api/resumes/1", "GET", Set.of())).isTrue();
    }

    @Test
    @DisplayName("ALL 규칙은 메서드를 가리지 않는다")
    void allMethodRuleMatchesEveryMethod() {
        urlAccessQuery.rules(Realm.ADMIN, rule("/api/resumes/**", "ALL", Set.of("RESUME_READ")));

        assertThat(service.hasAccess(Realm.ADMIN, "/api/resumes/1", "DELETE", Set.of("RESUME_READ"))).isTrue();
    }

    @Test
    @DisplayName("EMPLOYEE(fail-closed)는 규칙이 없는 보호 경로를 거부한다")
    void employeeDeniesUnmappedProtectedPath() {
        urlAccessQuery.rules(Realm.ADMIN);

        assertThat(service.hasAccess(Realm.ADMIN, "/api/unknown", "GET", Set.of("ANY"))).isFalse();
        assertThat(service.hasAccess(Realm.ADMIN, "/page/unknown", "GET", Set.of("ANY"))).isFalse();
    }

    @Test
    @DisplayName("EMPLOYEE 라도 보호 경로가 아니면 규칙이 없어도 통과한다")
    void employeeAllowsNonProtectedPath() {
        urlAccessQuery.rules(Realm.ADMIN);

        assertThat(service.hasAccess(Realm.ADMIN, "/css/app.css", "GET", Set.of())).isTrue();
    }

    @Test
    @DisplayName("보호 경로 접두어는 설정으로 바뀐다")
    void protectedPrefixesAreConfigurable() {
        // 어떤 경로가 보호 대상인지는 호스트의 URL 설계다. 공유 커널에 박아두지 않는다.
        AccessControlService custom = new AccessControlService(
                urlAccessQuery, new FakeSubjectRoleQuery(), NO_EXPIRY, List.of("/admin/"));
        urlAccessQuery.rules(Realm.ADMIN);

        assertThat(custom.hasAccess(Realm.ADMIN, "/admin/users", "GET", Set.of())).isFalse();
        assertThat(custom.hasAccess(Realm.ADMIN, "/api/unknown", "GET", Set.of())).isTrue();
    }

    @Test
    @DisplayName("CUSTOMER(fail-open)는 규칙이 없는 보호 경로를 허용한다")
    void customerAllowsUnmappedProtectedPath() {
        urlAccessQuery.rules(Realm.PORTAL);

        assertThat(service.hasAccess(Realm.PORTAL, "/api/unknown", "GET", Set.of())).isTrue();
    }

    @Test
    @DisplayName("규칙은 realm 별로 캐시되어 realm 하나를 조회해도 다른 realm 을 읽지 않는다")
    void rulesAreCachedPerRealm() {
        urlAccessQuery.rules(Realm.ADMIN, rule("/api/a/**", "ALL", Set.of("A")));

        service.hasAccess(Realm.ADMIN, "/api/a/1", "GET", Set.of("A"));
        service.hasAccess(Realm.ADMIN, "/api/a/2", "GET", Set.of("A"));

        assertThat(urlAccessQuery.loadsFor(Realm.ADMIN)).isEqualTo(1);
        assertThat(urlAccessQuery.loadsFor(Realm.PORTAL)).isZero();
    }

    @Test
    @DisplayName("reload 하면 바뀐 규칙이 즉시 반영된다")
    void reloadRefreshesCache() {
        urlAccessQuery.rules(Realm.ADMIN, rule("/api/a/**", "ALL", Set.of("A")));
        assertThat(service.hasAccess(Realm.ADMIN, "/api/a/1", "GET", Set.of("B"))).isFalse();

        urlAccessQuery.rules(Realm.ADMIN, rule("/api/a/**", "ALL", Set.of("B")));
        service.reload(Realm.ADMIN);

        assertThat(service.hasAccess(Realm.ADMIN, "/api/a/1", "GET", Set.of("B"))).isTrue();
    }

    @Test
    @DisplayName("TTL 이 지나면 reload 없이도 규칙을 다시 읽는다")
    void expiredCacheIsReloaded() throws InterruptedException {
        // reload 는 그 호출을 받은 프로세스만 갱신한다. 인스턴스가 여럿이면 나머지는 TTL 로 따라잡는다.
        AccessControlService shortTtl = new AccessControlService(
                urlAccessQuery, new FakeSubjectRoleQuery(), 1L, PROTECTED_PREFIXES);
        urlAccessQuery.rules(Realm.ADMIN, rule("/api/a/**", "ALL", Set.of("A")));
        assertThat(shortTtl.hasAccess(Realm.ADMIN, "/api/a/1", "GET", Set.of("B"))).isFalse();

        urlAccessQuery.rules(Realm.ADMIN, rule("/api/a/**", "ALL", Set.of("B")));
        Thread.sleep(1100);

        assertThat(shortTtl.hasAccess(Realm.ADMIN, "/api/a/1", "GET", Set.of("B"))).isTrue();
    }

    private static final class FakeUrlAccessQuery implements UrlAccessQuery {
        private final Map<Realm, List<UrlRule>> byRealm = new EnumMap<>(Realm.class);
        private final Map<Realm, Integer> loads = new EnumMap<>(Realm.class);

        void rules(Realm realm, UrlRule... rules) {
            byRealm.put(realm, List.of(rules));
        }

        int loadsFor(Realm realm) {
            return loads.getOrDefault(realm, 0);
        }

        @Override
        public List<UrlRule> activeRules(Realm realm) {
            loads.merge(realm, 1, Integer::sum);
            return byRealm.getOrDefault(realm, List.of());
        }

        @Override
        public List<UrlAccessView> listActive(Realm realm) {
            return List.of();
        }

        @Override
        public Optional<UrlAccessView> findById(Long urlAccessId) {
            return Optional.empty();
        }

        @Override
        public List<UrlResourceView> listByPermissionId(Long permissionId) {
            return List.of();
        }
    }

    private static final class FakeSubjectRoleQuery implements SubjectRoleQuery {
        @Override
        public List<String> permissionCodes(Realm realm, String subjectId) {
            return new ArrayList<>();
        }

        @Override
        public List<RoleView> assignedRoles(Realm realm, String subjectId) {
            return List.of();
        }
    }
}
