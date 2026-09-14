package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * identity_email_account — 이메일(OTP) 로그인 식별자. principal_id 로 Principal 과 연결.
 *
 * <p>email 은 전역이 아니라 (realm, email) 로 유일하다. 같은 사람이 직원이면서 포탈 고객일 수 있다.
 */
@Entity
@Table(name = "identity_email_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identity_email_account_realm_email",
                columnNames = {"realm", "email"}))
@Getter
@Setter
@NoArgsConstructor
public class EmailAccountJpaEntity {

    @Id
    @Column(name = "email_account_id", length = 36)
    private String emailAccountId;

    @Column(name = "principal_id", nullable = false, length = 36)
    private String principalId;

    @Column(name = "realm", nullable = false, length = 20)
    private String realm;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * 이 주소의 소유가 확인됐는가. 인증번호를 받아냈거나 provider 가 검증한 경우에만 참이다.
     * 비밀번호 가입 폼에 적힌 주소나 관리자가 대신 입력한 주소는 거짓이다 — 남의 것일 수 있다.
     */
    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
