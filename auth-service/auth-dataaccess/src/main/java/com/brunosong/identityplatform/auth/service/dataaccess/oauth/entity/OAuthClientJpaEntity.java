package com.brunosong.identityplatform.auth.service.dataaccess.oauth.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * oauth_client - 이 realm 에 로그인을 요청할 수 있는 앱.
 *
 * <p>돌아갈 주소는 값의 집합이라 별도 엔티티를 두지 않고 {@code @ElementCollection} 으로 붙인다.
 * 주소 한 줄에 식별자도 수명도 따로 없고, 앱을 지우면 같이 사라지는 것이 맞다.
 *
 * <p>한 앱을 통째로 읽는 조회뿐이라 즉시 로딩이다. 주소는 앱마다 한두 줄이고, 지연 로딩으로 두면
 * 도메인으로 옮기는 자리마다 트랜잭션이 열려 있어야 한다.
 *
 * <p>대신 목록 조회에서 앱마다 주소 조회가 한 번씩 따라붙는다(N+1). {@code @BatchSize} 가 그것을
 * {@code in (...)} 한 번으로 묶는다. 앱이 100개면 101번이 2번이 된다.
 *
 * <p>created_at / updated_at 은 도메인이 쓰지 않는다. 언제 등록됐는지는 운영에서 들여다볼 때
 * 필요한 값이라 엔티티가 직접 채운다.
 */
@Entity
@Table(name = "oauth_client")
@Getter
@Setter
@NoArgsConstructor
public class OAuthClientJpaEntity {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "realm", nullable = false, length = 20)
    private String realm;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** 비어 있으면 public client 다. 원문이 아니라 해시다. */
    @Column(name = "client_secret_hash", length = 64)
    private String clientSecretHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "oauth_client_redirect_uri",
            joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "redirect_uri", nullable = false, length = 500)
    private Set<String> redirectUris = new HashSet<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
