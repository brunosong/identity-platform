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
 * identity_login_session - 브라우저가 쿠키로 들고 다니는 로그인 상태.
 *
 * <p>감사 시각을 엔티티가 채우지 않는다. 만든 시각과 끝나는 시각 모두 도메인이 정하는 값이고,
 * 그 둘은 같은 계산에서 나온다(시작 시각 + 수명).
 */
@Entity
@Table(name = "identity_login_session")
@Getter
@Setter
@NoArgsConstructor
public class LoginSessionJpaEntity {

    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "realm", nullable = false, length = 20)
    private String realm;

    @Column(name = "principal_id", nullable = false, length = 36)
    private String principalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
