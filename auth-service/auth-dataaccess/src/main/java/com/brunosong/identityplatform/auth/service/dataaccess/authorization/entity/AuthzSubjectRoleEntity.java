package com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * authz_subject_role — 주체(직원/고객)와 역할의 배정. (realm, subject_id, role_id) 복합키.
 * subject_id 는 auth-agnostic 문자열(admin=esntlId, portal=customerUuid).
 */
@Entity
@Table(name = "authz_subject_role")
@IdClass(AuthzSubjectRoleEntity.AuthzSubjectRoleId.class)
@Getter
@Setter
@NoArgsConstructor
public class AuthzSubjectRoleEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "realm", length = 20)
    private Realm realm;

    @Id
    @Column(name = "subject_id", length = 64)
    private String subjectId;

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "assigned_at", updatable = false)
    private LocalDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private AuthzRoleEntity role;

    @PrePersist
    protected void onCreate() {
        assignedAt = LocalDateTime.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AuthzSubjectRoleId implements Serializable {
        private Realm realm;
        private String subjectId;
        private Long roleId;
    }
}
