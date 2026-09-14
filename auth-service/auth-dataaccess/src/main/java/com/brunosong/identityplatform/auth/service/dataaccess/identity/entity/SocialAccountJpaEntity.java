package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * identity_social_account — 소셜 로그인 자격증명. principal_id 로 Principal 과 연결.
 *
 * <p>연결은 {@code (realm, provider, provider_uid)} 로 유일하다 — 다른 자격증명 테이블과 같은 규칙이다.
 */
@Entity
@Table(name = "identity_social_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identity_social_account_realm_provider_uid",
                columnNames = {"realm", "provider", "provider_uid"}))
@Getter
@Setter
@NoArgsConstructor
public class SocialAccountJpaEntity {

    @Id
    @Column(name = "social_account_id", length = 36)
    private String socialAccountId;

    @Column(name = "principal_id", nullable = false, length = 36)
    private String principalId;

    @Column(name = "realm", nullable = false, length = 20)
    private String realm;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_uid", nullable = false, length = 191)
    private String providerUid;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
