package com.brunosong.identityplatform.auth.service.domain.authorization;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 역할 — realm 으로 스코프된다. 여기에 <b>어느 서비스의 역할인가</b>({@code clientId})가 더해진다.
 *
 * <ul>
 *   <li><b>realm 역할</b>({@code clientId == null}) — 영역 공통. 권한(permissionId)을 갖고,
 *       어드민 콘솔의 화면·URL 제어가 그것을 쓴다.</li>
 *   <li><b>client 역할</b>({@code clientId != null}) — 그 서비스의 것. <b>역할 코드 자체가 권한</b>이고
 *       별도의 permission 을 갖지 않는다. 그것이 무엇을 여는지는 그 서비스가 자기 코드로 정한다 —
 *       auth 는 이름만 보관한다.</li>
 * </ul>
 *
 * <p>서비스가 늘어날수록 권한 어휘가 전역이면 부딪힌다. 서비스로 네임스페이스를 가르면 그
 * 충돌이 구조적으로 불가능해지고, 새 서비스가 auth 스키마를 건드리지 않고 배포된다.
 * (Keycloak 의 client roles 와 같은 자리다.)
 *
 * <p>권한 집합의 변경은 이 애그리거트를 통해서만 일어난다. 밖에서 얻은 집합은 읽기 전용이라,
 * "조회해서 담아둔 집합을 몰래 고쳐 저장"하는 경로가 생기지 않는다.
 */
@Getter
public class Role {

    /** null = 아직 저장되지 않은 신규. */
    private final Long roleId;
    private final Realm realm;
    /** null = realm 공통 역할. 값이 있으면 그 서비스의 역할이다. */
    private final String clientId;
    private final String roleCode;

    private String roleName;
    private String description;
    private boolean active;

    private final Set<Long> permissionIds;

    private Role(Long roleId, Realm realm, String clientId, String roleCode, String roleName,
                 String description, boolean active, Collection<Long> permissionIds) {
        this.roleId = roleId;
        this.realm = realm;
        this.clientId = normalize(clientId);
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.active = active;
        this.permissionIds = permissionIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissionIds);
    }

    /** realm 공통 역할. 권한(permissionId)을 갖고 어드민 콘솔의 화면 제어가 그것을 쓴다. */
    public static Role create(Realm realm, String roleCode, String roleName, String description) {
        return create(realm, null, roleCode, roleName, description);
    }

    /** 그 서비스의 역할. {@code clientId} 가 null 이면 realm 공통 역할이 된다. */
    public static Role create(Realm realm, String clientId, String roleCode, String roleName,
                              String description) {
        if (realm == null) throw new IllegalArgumentException("realm must not be null");
        return new Role(null, realm, clientId, requireCode(roleCode), requireName(roleName),
                normalize(description), true, null);
    }

    public static Role restore(Long roleId, Realm realm, String clientId, String roleCode, String roleName,
                               String description, boolean active, Collection<Long> permissionIds) {
        return new Role(roleId, realm, clientId, roleCode, roleName, description, active, permissionIds);
    }

    /** 이 역할이 특정 서비스의 것인가. 아니면 realm 공통이다. */
    public boolean isClientRole() {
        return clientId != null;
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
