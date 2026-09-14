package com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.dataaccess.support.YnBooleanConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * authz_role — realm 으로 스코프된 역할. role_code 는 realm 내에서 유일.
 * 권한은 authz_role_permission(N:M)으로 연관.
 */
@Entity
@Table(name = "authz_role",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_authz_role_realm_role_code",
                columnNames = {"realm", "role_code"}))
@Getter
@Setter
@NoArgsConstructor
public class AuthzRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "realm", nullable = false, length = 20)
    private Realm realm;

    /** null = realm 공통 역할. 값이 있으면 그 서비스의 역할이다(Keycloak 의 client role). */
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(name = "description", length = 500)
    private String description;

    @Convert(converter = YnBooleanConverter.class)
    @Column(name = "use_yn", nullable = false, length = 1)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "authz_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<AuthzPermissionEntity> permissions = new HashSet<>();

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
