package com.brunosong.identityplatform.auth.service.domain.authorization;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 권한 — realm 과 <b>서비스</b>로 스코프된다. permissionCode 는 그 서비스 안에서 유일하다.
 *
 * <p>{@code clientId} 가 이 권한이 어느 서비스의 것인지 말한다. null 이면 어드민 콘솔 자신의
 * 권한이다. 서비스가 늘수록 어휘가 전역이면 부딪히는데 — order-service 의 {@code READ} 와
 * customer-service 의 {@code READ} — 서비스로 가르면 그 충돌이 구조적으로 불가능해진다.
 *
 * <p><b>auth 는 이름만 보관한다.</b> 이 코드가 어떤 URL 을 여는지는 그 서비스가 자기 코드로
 * 정하고, 그 규칙은 그 서비스와 함께 배포된다. 토큰에서도 서비스별로 칸이 갈린다 —
 * {@code resource_access["customer-service"].roles}.
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
    /** null = 어드민 콘솔 자신의 권한. 값이 있으면 그 서비스의 권한이다. */
    private final String clientId;
    private final String permissionCode;
    private final LocalDateTime createdAt;

    private String permissionName;
    private String category;
    private String description;
    private boolean active;

    private Permission(Long permissionId, Realm realm, String clientId, String permissionCode,
                       String permissionName, String category, String description, boolean active,
                       LocalDateTime createdAt) {
        this.permissionId = permissionId;
        this.realm = realm;
        this.clientId = normalize(clientId);
        this.permissionCode = permissionCode;
        this.permissionName = permissionName;
        this.category = category;
        this.description = description;
        this.active = active;
        this.createdAt = createdAt;
    }

    /** 어드민 콘솔 자신의 권한. */
    public static Permission create(Realm realm, String permissionCode, String permissionName,
                                    String category, String description) {
        return create(realm, null, permissionCode, permissionName, category, description);
    }

    /** 그 서비스의 권한. {@code clientId} 가 null 이면 어드민 콘솔 자신의 권한이 된다. */
    public static Permission create(Realm realm, String clientId, String permissionCode,
                                    String permissionName, String category, String description) {
        requireRealm(realm);
        return new Permission(null, realm, clientId, requireCode(permissionCode),
                requireName(permissionName), normalize(category), normalize(description), true, null);
    }

    public static Permission restore(Long permissionId, Realm realm, String clientId, String permissionCode,
                                     String permissionName, String category, String description,
                                     boolean active, LocalDateTime createdAt) {
        return new Permission(permissionId, realm, clientId, permissionCode, permissionName,
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
