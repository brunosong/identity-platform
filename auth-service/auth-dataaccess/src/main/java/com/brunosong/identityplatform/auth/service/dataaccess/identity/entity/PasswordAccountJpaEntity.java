package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * identity_password_account — 비밀번호 자격증명(로그인ID/비번). principal_id 로 Principal 과 연결.
 *
 * <p>login_id 는 전역이 아니라 {@code (subject_type, login_id)} 로 유일하다 —
 * {@link EmailAccountJpaEntity} 와 같은 규칙이다. 같은 사람이 직원이면서 고객일 수 있고,
 * 무엇보다 유형 없이 조회하면 상대 realm 의 자격증명이 걸린다.
 */
@Entity
@Table(name = "identity_password_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identity_password_account_subject_type_login_id",
                columnNames = {"subject_type", "login_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PasswordAccountJpaEntity {

    @Id
    @Column(name = "password_account_id", length = 36)
    private String passwordAccountId;

    @Column(name = "principal_id", nullable = false, length = 36)
    private String principalId;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "login_id", nullable = false, length = 100)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;
}
