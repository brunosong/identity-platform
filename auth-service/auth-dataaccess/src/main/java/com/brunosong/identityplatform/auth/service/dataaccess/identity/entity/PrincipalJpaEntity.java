package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalStatus;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * principal — 인증 신원. principalId(UUID 문자열)가 PK. 주체는 (subject_type, subject_id)로 유일.
 */
@Entity
@Table(name = "identity_principal")
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
    @Column(name = "subject_type", nullable = false, length = 20)
    private SubjectType subjectType;

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
