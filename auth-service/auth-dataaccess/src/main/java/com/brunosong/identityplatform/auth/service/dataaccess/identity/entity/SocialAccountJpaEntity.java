package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * social_account — 소셜 로그인 자격증명. principal_id 로 Principal 과 연결.
 */
@Entity
@Table(name = "identity_social_account")
@Getter
@Setter
@NoArgsConstructor
public class SocialAccountJpaEntity {

    @Id
    @Column(name = "social_account_id", length = 36)
    private String socialAccountId;

    @Column(name = "principal_id", nullable = false, length = 36)
    private String principalId;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_uid", nullable = false, length = 191)
    private String providerUid;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
