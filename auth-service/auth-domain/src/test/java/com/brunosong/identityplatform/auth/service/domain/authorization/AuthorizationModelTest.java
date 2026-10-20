package com.brunosong.identityplatform.auth.service.domain.authorization;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


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
}
