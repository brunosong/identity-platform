package com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.dataaccess.support.YnBooleanConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * authz_permission — realm 으로 스코프된 권한. permission_code 는 realm 내에서 유일.
 */
@Entity
@Table(name = "authz_permission")
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
