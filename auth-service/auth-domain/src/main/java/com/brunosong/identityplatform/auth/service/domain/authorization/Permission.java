package com.brunosong.identityplatform.auth.service.domain.authorization;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 권한 — realm 으로 스코프되며 permissionCode 는 realm 내에서 유일하다.
 *
 * <p>식별자(realm, permissionCode)는 생성 후 바뀌지 않는다. 코드가 바뀌면 그것은 같은 권한의 수정이
 * 아니라 다른 권한이며, 이미 그 코드에 기대고 있던 URL 규칙과의 대응도 끊긴다.
 * 표시 정보(이름/분류/설명)와 사용 여부만 바꿀 수 있다.
 */
@Getter
public class Permission {

    /** null = 아직 저장되지 않은 신규. */
    private final Long permissionId;
    private final Realm realm;
    private final String permissionCode;
    private final LocalDateTime createdAt;

    private String permissionName;
    private String category;
    private String description;
    private boolean active;

    private Permission(Long permissionId, Realm realm, String permissionCode, String permissionName,
                       String category, String description, boolean active, LocalDateTime createdAt) {
        this.permissionId = permissionId;
        this.realm = realm;
        this.permissionCode = permissionCode;
        this.permissionName = permissionName;
        this.category = category;
        this.description = description;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static Permission create(Realm realm, String permissionCode, String permissionName,
                                    String category, String description) {
        requireRealm(realm);
        return new Permission(null, realm, requireCode(permissionCode), requireName(permissionName),
                normalize(category), normalize(description), true, null);
    }

    public static Permission restore(Long permissionId, Realm realm, String permissionCode, String permissionName,
                                     String category, String description, boolean active, LocalDateTime createdAt) {
        return new Permission(permissionId, realm, permissionCode, permissionName,
                category, description, active, createdAt);
    }

    /** 표시 정보 수정. 식별자(realm/permissionCode)는 대상이 아니다. */
    public void describeAs(String permissionName, String category, String description) {
        this.permissionName = requireName(permissionName);
        this.category = normalize(category);
        this.description = normalize(description);
    }

    public void activate() {
        this.active = true;
    }

    /** 비활성화 — 행은 남기고 인가 판정에서만 제외한다(발급된 토큰은 리비전 bump 로 무효화). */
    public void deactivate() {
        this.active = false;
    }

    private static void requireRealm(Realm realm) {
        if (realm == null) throw new IllegalArgumentException("realm must not be null");
    }

    private static String requireCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
        return permissionCode.trim();
    }

    private static String requireName(String permissionName) {
        if (permissionName == null || permissionName.isBlank()) {
            throw new IllegalArgumentException("permissionName must not be blank");
        }
        return permissionName.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
