package com.brunosong.identityplatform.auth.service.dataaccess.oauth.adapter;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인가 코드 저장 경로 테스트. 어댑터 -> 실제 PostgreSQL 왕복을 본다.
 *
 * <p>보는 지점은 하나다. <b>꺼내면 사라지는가.</b> 그것이 "코드는 한 번만 쓴다" 를 지탱한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuthorizationCodePersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class AuthorizationCodePersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    @Autowired
    private AuthorizationCodePersistenceAdapter adapter;

    private static AuthorizationCode issued() {
        AuthorizationRequest request = AuthorizationRequest.of("code", "portal",
                "http://localhost:5173/login/callback", "openid", "state-1", "challenge-1", "S256", "nonce-1");
        return AuthorizationCode.issue(Realm.PORTAL, request, new PrincipalId("p-1"), NOW);
    }

    @Test
    @DisplayName("저장한 코드를 값 그대로 꺼낸다")
    void roundTrip() {
        AuthorizationCode issued = issued();
        adapter.save(issued);

        AuthorizationCode loaded = adapter.consume(issued.getCode()).orElseThrow();

        assertThat(loaded.getCode()).isEqualTo(issued.getCode());
        assertThat(loaded.getRealm()).isEqualTo(Realm.PORTAL);
        assertThat(loaded.getClientId()).isEqualTo("portal");
        assertThat(loaded.getRedirectUri()).isEqualTo("http://localhost:5173/login/callback");
        assertThat(loaded.getCodeChallenge()).isEqualTo("challenge-1");
        assertThat(loaded.getPrincipalId().value()).isEqualTo("p-1");
        assertThat(loaded.getScope()).isEqualTo("openid");
        assertThat(loaded.getNonce()).isEqualTo("nonce-1");
        assertThat(loaded.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("두 번째로 꺼내면 비어 있다")
    void consumeOnlyOnce() {
        AuthorizationCode issued = issued();
        adapter.save(issued);

        assertThat(adapter.consume(issued.getCode())).isPresent();
        assertThat(adapter.consume(issued.getCode())).isEmpty();
    }

    @Test
    @DisplayName("없는 코드는 비어 있다")
    void unknownCode() {
        assertThat(adapter.consume("없는-코드")).isEmpty();
    }

    @Test
    @DisplayName("만료된 코드도 꺼내진다 - 쓸 수 있는지는 꺼낸 쪽이 본다")
    void expiredCodeIsStillConsumed() {
        // 여기서 걸러 버리면 "만료됐다" 와 "그런 코드가 없다" 가 구분되지 않는다.
        AuthorizationCode issued = issued();
        adapter.save(issued);

        AuthorizationCode loaded = adapter.consume(issued.getCode()).orElseThrow();

        assertThat(loaded.isExpired(NOW.plus(Duration.ofMinutes(2)))).isTrue();
    }
}
