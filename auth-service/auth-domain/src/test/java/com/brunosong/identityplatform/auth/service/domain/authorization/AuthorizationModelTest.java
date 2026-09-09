package com.brunosong.identityplatform.auth.service.domain.authorization;

import com.brunosong.identityplatform.auth.service.domain.authorization.valueobject.HttpMethodPattern;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인가 모델이 스스로 지키는 규칙을 고정한다.
 *
 * <p>이 모델들은 예전에 setter 만 있는 자료 묶음이었다. 그때는 realm 없는 역할이나 코드가 빈 권한도
 * 만들어졌고, 그 검사를 저장 직전 어딘가에서 하기를 기대했다. 지금은 만들 수 없다.
 */
class AuthorizationModelTest {

    @Nested
    @DisplayName("역할")
    class RoleTest {

        @Test
        @DisplayName("권한 없이 시작해도 빈 집합을 갖는다")
        void startsWithEmptyPermissions() {
            Role role = Role.create(Realm.ADMIN, "ADMIN", "관리자", null);

            assertThat(role.getPermissionIds()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("권한 집합은 넣은 순서를 유지한다")
        void keepsInsertionOrder() {
            // 화면이 권한 목록을 그대로 보여준다. 순서가 흔들리면 diff 가 매번 달라 보인다.
            Role role = Role.create(Realm.ADMIN, "ADMIN", "관리자", null);

            role.grantPermission(3L);
            role.grantPermission(1L);
            role.grantPermission(2L);

            assertThat(role.getPermissionIds()).containsExactly(3L, 1L, 2L);
        }

        @Test
        @DisplayName("밖에서 얻은 권한 집합은 고칠 수 없다")
        void permissionsAreReadOnlyOutside() {
            // 조회해 둔 집합을 몰래 고쳐 저장하는 경로를 막는다 — 변경은 애그리거트를 통해서만.
            Role role = Role.create(Realm.ADMIN, "ADMIN", "관리자", null);

            assertThatThrownBy(() -> role.getPermissionIds().add(1L))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("이미 가진 권한을 다시 부여하면 변경 없음을 알린다")
        void grantIsIdempotent() {
            // 호출부가 이 값으로 불필요한 저장을 건너뛴다.
            Role role = Role.create(Realm.ADMIN, "ADMIN", "관리자", null);

            assertThat(role.grantPermission(1L)).isTrue();
            assertThat(role.grantPermission(1L)).isFalse();
            assertThat(role.revokePermission(1L)).isTrue();
            assertThat(role.revokePermission(1L)).isFalse();
        }

        @Test
        @DisplayName("realm 과 코드 없이는 만들 수 없다")
        void requiresRealmAndCode() {
            assertThatThrownBy(() -> Role.create(null, "ADMIN", "관리자", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Role.create(Realm.ADMIN, " ", "관리자", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Role.create(Realm.ADMIN, "ADMIN", " ", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("realm 으로 스코프된다")
        void scopedByRealm() {
            // role_code 는 realm 안에서만 유일하다. 같은 코드가 두 realm 에 있을 수 있다.
            Role employeeRole = Role.create(Realm.ADMIN, "ADMIN", "관리자", null);
            Role customerRole = Role.create(Realm.PORTAL, "ADMIN", "관리자", null);

            assertThat(employeeRole.getRoleCode()).isEqualTo(customerRole.getRoleCode());
            assertThat(employeeRole.getRealm()).isNotEqualTo(customerRole.getRealm());
        }
    }

    @Nested
    @DisplayName("권한")
    class PermissionTest {

        @Test
        @DisplayName("새로 만들면 활성 상태다")
        void createdActive() {
            Permission permission = Permission.create(Realm.ADMIN, "AUTHZ_MANAGE", "인가 관리", "AUTHZ", null);

            assertThat(permission.isActive()).isTrue();
            assertThat(permission.getPermissionId()).isNull();
        }

        @Test
        @DisplayName("표시 정보만 바뀌고 식별자는 그대로다")
        void describeKeepsIdentity() {
            Permission permission = Permission.create(Realm.ADMIN, "AUTHZ_MANAGE", "인가 관리", "AUTHZ", null);

            permission.describeAs("인가 관리(신)", "SYSTEM", "설명");

            assertThat(permission.getPermissionName()).isEqualTo("인가 관리(신)");
            assertThat(permission.getPermissionCode()).isEqualTo("AUTHZ_MANAGE");
            assertThat(permission.getRealm()).isEqualTo(Realm.ADMIN);
        }

        @Test
        @DisplayName("코드가 비면 만들 수 없다")
        void requiresCode() {
            assertThatThrownBy(() -> Permission.create(Realm.ADMIN, "", "인가 관리", "AUTHZ", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("URL 접근 규칙")
    class UrlAccessTest {

        @Test
        @DisplayName("권한 없이 시작해도 빈 목록을 갖는다")
        void startsWithEmptyPermissions() {
            UrlAccess access = UrlAccess.create(Realm.PORTAL, "/api/resumes/**", "GET", null, 0);

            assertThat(access.getPermissionCodes()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("메서드를 비우면 ALL 로 읽는다")
        void blankMethodMeansAll() {
            UrlAccess access = UrlAccess.create(Realm.PORTAL, "/api/resumes/**", " ", null, 0);

            assertThat(access.getHttpMethod()).isEqualTo(HttpMethodPattern.ALL);
        }

        @Test
        @DisplayName("캐시 규칙으로 투영한다")
        void projectsToRule() {
            UrlAccess access = UrlAccess.restore(1L, Realm.ADMIN, "/api/admin/**", "get",
                    null, true, 0, null, List.of("ADMIN_READ"));

            UrlRule rule = access.toRule();

            assertThat(rule.urlPattern()).isEqualTo("/api/admin/**");
            assertThat(rule.matchesMethod("GET")).isTrue();
            assertThat(rule.permissionCodes()).containsExactly("ADMIN_READ");
        }
    }

    @Nested
    @DisplayName("캐시 규칙")
    class UrlRuleTest {

        @Test
        @DisplayName("URL·메서드와 허용 권한 집합을 함께 담는다")
        void carriesPermissions() {
            UrlRule rule = new UrlRule("/api/admin/**", HttpMethodPattern.of("GET"),
                    Set.of("ADMIN_READ", "ADMIN_ALL"));

            assertThat(rule.urlPattern()).isEqualTo("/api/admin/**");
            assertThat(rule.httpMethod().value()).isEqualTo("GET");
            assertThat(rule.permissionCodes()).containsExactlyInAnyOrder("ADMIN_READ", "ADMIN_ALL");
        }

        @Test
        @DisplayName("같은 내용의 규칙은 같은 값으로 본다")
        void valueEquality() {
            // 규칙 캐시를 갱신할 때 바뀐 것만 추리는 근거다.
            UrlRule one = new UrlRule("/api/admin/**", HttpMethodPattern.of("GET"), Set.of("ADMIN_READ"));
            UrlRule other = new UrlRule("/api/admin/**", HttpMethodPattern.of("GET"), Set.of("ADMIN_READ"));

            assertThat(one).isEqualTo(other);
        }

        @Test
        @DisplayName("만든 뒤에는 권한 집합을 고칠 수 없다")
        void permissionsAreImmutable() {
            // 이 규칙은 인가 판정 캐시에 그대로 들어간다. 나중에 누가 한 건 넣으면 그 순간부터
            // 요청마다 다른 판정이 나오고, 원인을 찾기 어렵다.
            UrlRule rule = new UrlRule("/api/admin/**", HttpMethodPattern.of("GET"), Set.of("ADMIN_READ"));

            assertThatThrownBy(() -> rule.permissionCodes().add("SNEAKY"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("보유 권한 중 하나라도 맞으면 통과한다")
        void allowsAnyIsOr() {
            UrlRule rule = new UrlRule("/api/admin/**", HttpMethodPattern.of("GET"),
                    Set.of("ADMIN_READ", "ADMIN_ALL"));

            assertThat(rule.allowsAny(Set.of("ADMIN_ALL"))).isTrue();
            assertThat(rule.allowsAny(Set.of("OTHER"))).isFalse();
            assertThat(rule.allowsAny(Set.of())).isFalse();
        }

        @Test
        @DisplayName("권한 매핑이 없는 규칙은 아무도 통과시키지 않는다")
        void emptyRuleAllowsNobody() {
            UrlRule rule = new UrlRule("/api/admin/**", HttpMethodPattern.ALL, Set.of());

            assertThat(rule.allowsAny(Set.of("ADMIN_ALL"))).isFalse();
        }
    }

    @Nested
    @DisplayName("HTTP 메서드 패턴")
    class HttpMethodPatternTest {

        @Test
        @DisplayName("ALL 은 어떤 메서드와도 맞는다")
        void allMatchesEverything() {
            assertThat(HttpMethodPattern.ALL.matches("GET")).isTrue();
            assertThat(HttpMethodPattern.ALL.matches("DELETE")).isTrue();
            assertThat(HttpMethodPattern.ALL.matches(null)).isTrue();
        }

        @Test
        @DisplayName("대소문자를 가리지 않는다")
        void caseInsensitive() {
            assertThat(HttpMethodPattern.of("get").matches("GET")).isTrue();
            assertThat(HttpMethodPattern.of("GET").matches("get")).isTrue();
            assertThat(HttpMethodPattern.of("GET").matches("POST")).isFalse();
        }

        @Test
        @DisplayName("빈 값은 ALL 로 정규화된다")
        void blankIsAll() {
            assertThat(HttpMethodPattern.of(null)).isEqualTo(HttpMethodPattern.ALL);
            assertThat(HttpMethodPattern.of("  ")).isEqualTo(HttpMethodPattern.ALL);
        }
    }
}
