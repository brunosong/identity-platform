package com.brunosong.identityplatform.auth.service.domain.authorization;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 역할 — realm 으로 스코프되며 roleCode 는 realm 내에서 유일하다.
 * 보유 권한은 permissionId 집합으로만 연관한다(권한은 별도 애그리거트).
 *
 * <p><b>역할에는 서비스 구분이 없다.</b> 역할은 "이 사람이 조직에서 맡은 일"(ADMIN, CUSTOMER,
 * 상담원)이고 그것은 서비스마다 달라지지 않는다. 서비스마다 갈리는 것은 {@link Permission} 이다 —
 * 한 역할이 여러 서비스의 권한을 가질 수 있고, 그 배정이 여기 permissionIds 다.
 *
 * <p>권한 집합의 변경은 이 애그리거트를 통해서만 일어난다. 밖에서 얻은 집합은 읽기 전용이라,
 * "조회해서 담아둔 집합을 몰래 고쳐 저장"하는 경로가 생기지 않는다.
 */
@Getter
public class Role {

    /** null = 아직 저장되지 않은 신규. */
    private final Long roleId;
    private final Realm realm;
    private final String roleCode;

    private String roleName;
    private String description;
    private boolean active;

    private final Set<Long> permissionIds;

    private Role(Long roleId, Realm realm, String roleCode, String roleName, String description,
                 boolean active, Collection<Long> permissionIds) {
        this.roleId = roleId;
        this.realm = realm;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.active = active;
        this.permissionIds = permissionIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissionIds);
    }

    public static Role create(Realm realm, String roleCode, String roleName, String description) {
        if (realm == null) throw new IllegalArgumentException("realm must not be null");
        return new Role(null, realm, requireCode(roleCode), requireName(roleName), normalize(description),
                true, null);
    }

    public static Role restore(Long roleId, Realm realm, String roleCode, String roleName, String description,
                               boolean active, Collection<Long> permissionIds) {
        return new Role(roleId, realm, roleCode, roleName, description, active, permissionIds);
    }

    /** 보유 권한 — 읽기 전용 뷰. 변경은 {@link #replacePermissions}/{@link #grantPermission} 으로만. */
    public Set<Long> getPermissionIds() {
        return Collections.unmodifiableSet(permissionIds);
    }

    /** 표시 정보 수정. 식별자(realm/roleCode)는 대상이 아니다. */
    public void describeAs(String roleName, String description) {
        this.roleName = requireName(roleName);
        this.description = normalize(description);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    /** 권한 매핑을 입력 집합으로 교체한다. */
    public void replacePermissions(Collection<Long> permissionIds) {
        this.permissionIds.clear();
        if (permissionIds != null) {
            permissionIds.stream().filter(java.util.Objects::nonNull).forEach(this.permissionIds::add);
        }
    }

    /** 권한 1건 부여. 이미 있으면 false(변경 없음). */
    public boolean grantPermission(Long permissionId) {
        if (permissionId == null) throw new IllegalArgumentException("permissionId must not be null");
        return permissionIds.add(permissionId);
    }

    /** 권한 1건 회수. 없었으면 false(변경 없음). */
    public boolean revokePermission(Long permissionId) {
        return permissionId != null && permissionIds.remove(permissionId);
    }

    public boolean hasPermission(Long permissionId) {
        return permissionIds.contains(permissionId);
    }

    private static String requireCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("roleCode must not be blank");
        }
        return roleCode.trim();
    }

    private static String requireName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("roleName must not be blank");
        }
        return roleName.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
