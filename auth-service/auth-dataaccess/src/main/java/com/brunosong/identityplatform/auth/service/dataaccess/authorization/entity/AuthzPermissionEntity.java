package com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.dataaccess.support.YnBooleanConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * authz_permission — realm 과 서비스로 스코프된 권한. permission_code 는 그 서비스 안에서 유일.
 *
 * <p>유니크는 부분 인덱스 둘로 갈려 있다(client_id 가 null 인 것과 아닌 것). JPA 의
 * {@code @UniqueConstraint} 로는 표현되지 않으므로 여기에 선언하지 않고 스키마에만 둔다.
 */
@Entity
@Table(name = "authz_permission",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_authz_permission_realm_client_code",
                columnNames = {"realm", "client_id", "permission_code"}))
@Getter
@Setter
@NoArgsConstructor
public class AuthzPermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long permissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "realm", nullable = false, length = 20)
    private Realm realm;

    /** null = 어드민 콘솔 자신의 권한. 값이 있으면 그 서비스의 권한이다. */
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Column(name = "permission_name", nullable = false, length = 200)
    private String permissionName;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    @Convert(converter = YnBooleanConverter.class)
    @Column(name = "use_yn", nullable = false, length = 1)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
