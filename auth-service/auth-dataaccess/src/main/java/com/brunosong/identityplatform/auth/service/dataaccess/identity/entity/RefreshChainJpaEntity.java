package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * identity_refresh_chain - 로그인 하나에서 이어져 내려온 refresh 토큰의 계보.
 *
 * <p>시각을 엔티티가 채우지 않는다. 연 시각과 끝나는 시각 모두 도메인이 정한다.
 */
@Entity
@Table(name = "identity_refresh_chain")
@Getter
@Setter
@NoArgsConstructor
public class RefreshChainJpaEntity {

    @Id
    @Column(name = "family_id", length = 36)
    private String familyId;

    @Column(name = "realm", nullable = false, length = 20)
    private String realm;

    @Column(name = "subject_id", nullable = false, length = 36)
    private String subjectId;

    @Column(name = "current_jti", nullable = false, length = 36)
    private String currentJti;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
