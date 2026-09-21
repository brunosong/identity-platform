package com.brunosong.identityplatform.auth.service.dataaccess.oauth.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
 * <p>created_at / updated_at 은 매핑하지 않는다. 지금 이 경로는 읽기만 하고, 도메인도 그 값을
 * 쓰지 않는다. 등록 화면이 생기면 그때 붙인다.
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "oauth_client_redirect_uri",
            joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "redirect_uri", nullable = false, length = 500)
    private Set<String> redirectUris = new HashSet<>();
}
