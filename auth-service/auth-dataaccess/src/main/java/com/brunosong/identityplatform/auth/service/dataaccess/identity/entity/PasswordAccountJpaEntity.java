package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * password_account — 비밀번호 자격증명(로그인ID/비번). principal_id 로 Principal 과 연결.
 */
@Entity
@Table(name = "identity_password_account")
@Getter
@Setter
@NoArgsConstructor
public class PasswordAccountJpaEntity {

    @Id
    @Column(name = "password_account_id", length = 36)
    private String passwordAccountId;

    @Column(name = "principal_id", nullable = false, length = 36)
    private String principalId;

    @Column(name = "login_id", nullable = false, unique = true, length = 100)
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
