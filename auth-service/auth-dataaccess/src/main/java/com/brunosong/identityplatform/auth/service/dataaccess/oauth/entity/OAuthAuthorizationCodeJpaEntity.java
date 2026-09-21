package com.brunosong.identityplatform.auth.service.dataaccess.oauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * oauth_authorization_code - 로그인이 끝난 직후 앱에게 건네는 1회용 증서.
 *
 * <p>행이 오래 살지 않는다. 쓰이면 그 자리에서 지워지고, 안 쓰이면 1분 뒤 만료된 채 남는다.
 */
@Entity
@Table(name = "oauth_authorization_code")
@Getter
@Setter
@NoArgsConstructor
public class OAuthAuthorizationCodeJpaEntity {

    @Id
    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "realm", nullable = false, length = 20)
    private String realm;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "redirect_uri", nullable = false, length = 500)
    private String redirectUri;

    @Column(name = "code_challenge", nullable = false, length = 128)
    private String codeChallenge;

    @Column(name = "principal_id", nullable = false, length = 36)
    private String principalId;

    @Column(name = "scope", length = 200)
    private String scope;

    @Column(name = "nonce", length = 200)
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
