package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalStatus;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * identity_principal — 인증 신원. principalId(UUID 문자열)가 PK. 주체는 (realm, subject_id)로 유일.
 *
 * <p>그 유일성을 제약으로 건다. 전에는 주석에만 있었다 — subject_id 는 realm 마다 다른 체계에서
 * 발급되므로(직원 esntlId, 고객 UUID) 전역 유일을 걸 수 없고, 유형과 묶어야 비로소 유일해진다.
 */
@Entity
@Table(name = "identity_principal",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identity_principal_realm_subject_id",
                columnNames = {"realm", "subject_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PrincipalJpaEntity {

    @Id
    @Column(name = "principal_id", length = 36)
    private String principalId;

    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "realm", nullable = false, length = 20)
    private Realm realm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PrincipalStatus status = PrincipalStatus.ACTIVE;

    @Column(name = "last_authenticated_at")
    private Instant lastAuthenticatedAt;

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
