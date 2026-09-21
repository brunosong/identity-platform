package com.brunosong.identityplatform.auth.service.dataaccess.oauth.adapter;

import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 등록된 앱을 읽어오는 경로 테스트. 어댑터 -> 실제 PostgreSQL 왕복을 본다.
 *
 * <p>보는 지점은 두 가지다. 돌아갈 주소가 여러 줄이어도 한 앱으로 모여 오는가, 그리고 realm 이
 * 어긋난 앱은 없는 것과 같은가. 뒤쪽이 이 조회가 realm 을 받는 이유다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OAuthClientPersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class OAuthClientPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private OAuthClientPersistenceAdapter adapter;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void insertClients() {
        insertClient("portal", "PORTAL", true);
        insertRedirectUri("portal", "http://localhost:5173/login/callback");
        insertRedirectUri("portal", "http://localhost:5173/login/silent");
        insertClient("backoffice", "ADMIN", false);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("등록된 앱을 주소까지 함께 읽어온다")
    void loadsClientWithRedirectUris() {
        OAuthClient client = adapter.findByClientId(Realm.PORTAL, "portal").orElseThrow();

        assertThat(client.getClientId()).isEqualTo("portal");
        assertThat(client.getRealm()).isEqualTo(Realm.PORTAL);
        assertThat(client.isEnabled()).isTrue();
        assertThat(client.allowsRedirect("http://localhost:5173/login/callback")).isTrue();
        assertThat(client.allowsRedirect("http://localhost:5173/login/silent")).isTrue();
    }

    @Test
    @DisplayName("realm 이 어긋나면 없는 것과 같다")
    void otherRealmClientIsNotFound() {
        // 고객 앱의 client_id 로 어드민 로그인을 시작하려는 요청이 여기서 끝난다.
        assertThat(adapter.findByClientId(Realm.ADMIN, "portal")).isEmpty();
    }

    @Test
    @DisplayName("등록되지 않은 앱은 비어 있다")
    void unknownClientIsEmpty() {
        assertThat(adapter.findByClientId(Realm.PORTAL, "no-such-app")).isEmpty();
    }

    @Test
    @DisplayName("꺼진 앱도 읽어온다 - 거절은 조회가 아니라 그 다음이 한다")
    void disabledClientIsStillLoaded() {
        OAuthClient client = adapter.findByClientId(Realm.ADMIN, "backoffice").orElseThrow();

        assertThat(client.isEnabled()).isFalse();
    }

    private void insertClient(String clientId, String realm, boolean enabled) {
        em.createNativeQuery("""
                        INSERT INTO oauth_client (client_id, realm, enabled, created_at, updated_at)
                        VALUES (?, ?, ?, now(), now())
                        """)
                .setParameter(1, clientId)
                .setParameter(2, realm)
                .setParameter(3, enabled)
                .executeUpdate();
    }

    private void insertRedirectUri(String clientId, String redirectUri) {
        em.createNativeQuery(
                        "INSERT INTO oauth_client_redirect_uri (client_id, redirect_uri) VALUES (?, ?)")
                .setParameter(1, clientId)
                .setParameter(2, redirectUri)
                .executeUpdate();
    }
}
