package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.domain.identity.LoginSession;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
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

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 세션 저장 경로 테스트.
 *
 * <p>인가 코드와 반대로 <b>여러 번 읽는다</b>. 앱을 옮길 때마다 다시 보는 값이라, 읽었다고
 * 사라지면 두 번째 앱에서 다시 로그인하게 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LoginSessionPersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class LoginSessionPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final Instant NOW = Instant.parse("2026-09-22T09:00:00Z");

    @Autowired
    private LoginSessionPersistenceAdapter adapter;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("저장한 세션을 값 그대로 꺼낸다")
    void roundTrip() {
        LoginSession session = LoginSession.start(Realm.PORTAL, new PrincipalId("p-1"), NOW);
        adapter.save(session);
        em.flush();
        em.clear();

        LoginSession loaded = adapter.findById(session.getSessionId()).orElseThrow();

        assertThat(loaded.getSessionId()).isEqualTo(session.getSessionId());
        assertThat(loaded.getRealm()).isEqualTo(Realm.PORTAL);
        assertThat(loaded.getPrincipalId().value()).isEqualTo("p-1");
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(loaded.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));
    }

    @Test
    @DisplayName("읽어도 사라지지 않는다")
    void readDoesNotConsume() {
        // 인가 코드와 다른 점이다. 앱을 옮길 때마다 다시 읽는 값이다.
        LoginSession session = LoginSession.start(Realm.PORTAL, new PrincipalId("p-1"), NOW);
        adapter.save(session);
        em.flush();
        em.clear();

        assertThat(adapter.findById(session.getSessionId())).isPresent();
        assertThat(adapter.findById(session.getSessionId())).isPresent();
    }

    @Test
    @DisplayName("없는 세션은 비어 있다")
    void unknownSession() {
        assertThat(adapter.findById("없는-세션")).isEmpty();
    }

    @Test
    @DisplayName("만료된 세션도 읽힌다 - 쓸 수 있는지는 읽은 쪽이 본다")
    void expiredSessionIsStillReadable() {
        LoginSession old = LoginSession.start(Realm.ADMIN, new PrincipalId("p-2"),
                NOW.minus(Duration.ofHours(9)));
        adapter.save(old);
        em.flush();
        em.clear();

        LoginSession loaded = adapter.findById(old.getSessionId()).orElseThrow();

        assertThat(loaded.isExpired(NOW)).isTrue();
    }

    @Test
    @DisplayName("지운 세션은 사라진다")
    void deleteRemovesSession() {
        LoginSession session = LoginSession.start(Realm.PORTAL, new PrincipalId("p-1"), NOW);
        adapter.save(session);
        em.flush();

        adapter.delete(session.getSessionId());
        em.flush();
        em.clear();

        assertThat(adapter.findById(session.getSessionId())).isEmpty();
    }

    @Test
    @DisplayName("없는 세션을 지워도 조용히 지나간다")
    void deleteUnknownIsQuiet() {
        // 만료된 쿠키를 들고 온 사람도 로그아웃할 수 있어야 한다.
        adapter.delete("없는-세션");
    }
}
