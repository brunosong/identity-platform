package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * identity_email_account — 이메일(OTP) 로그인 식별자. principal_id 로 Principal 과 연결.
 *
 * <p>email 은 전역이 아니라 (subject_type, email) 로 유일하다. 같은 사람이 직원이면서 포탈 고객일 수 있다.
 */
@Entity
@Table(name = "identity_email_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identity_email_account_subject_type_email",
                columnNames = {"subject_type", "email"}))
@Getter
@Setter
@NoArgsConstructor
public class EmailAccountJpaEntity {

    @Id
    @Column(name = "email_account_id", length = 36)
    private String emailAccountId;

    @Column(name = "principal_id", nullable = false, length = 36)
    private String principalId;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
