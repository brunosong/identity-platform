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
 * authz_url_access — realm 으로 스코프된 (URL 패턴, HTTP 메서드) 리소스.
 * 허용 권한은 authz_url_permission(N:M, OR)으로 연관.
 */
@Entity
@Table(name = "authz_url_access")
@Getter
@Setter
@NoArgsConstructor
public class AuthzUrlAccessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "url_access_id")
    private Long urlAccessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "realm", nullable = false, length = 20)
    private Realm realm;

    @Column(name = "url_pattern", nullable = false, length = 300)
    private String urlPattern;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod = "ALL";

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "authz_url_permission",
            joinColumns = @JoinColumn(name = "url_access_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<AuthzPermissionEntity> permissions = new HashSet<>();

    @Column(name = "description", length = 500)
    private String description;

    @Convert(converter = YnBooleanConverter.class)
    @Column(name = "use_yn", nullable = false, length = 1)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

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
