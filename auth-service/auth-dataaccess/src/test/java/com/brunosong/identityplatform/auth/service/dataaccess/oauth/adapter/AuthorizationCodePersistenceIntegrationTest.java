package com.brunosong.identityplatform.auth.service.dataaccess.oauth.adapter;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인가 코드 저장 경로 테스트. 어댑터 -> 실제 PostgreSQL 왕복을 본다.
 *
 * <p>보는 지점은 <b>꺼내면 사라지는가</b>이고, 그것이 "코드는 한 번만 쓴다" 를 지탱한다.
 *
 * <p>테스트를 트랜잭션으로 감싸지 않는다({@code NOT_SUPPORTED}). 꺼내는 일이 독립 트랜잭션에서
 * 커밋되기 때문에, 감싸면 아직 커밋되지 않은 저장을 그쪽에서 볼 수가 없다. 실제로도 저장과 교환은
 * 다른 요청이라 그 사이에 커밋이 있다. 대신 앞뒤로 표를 직접 비운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuthorizationCodePersistenceAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clear() {
        execute("DELETE FROM oauth_authorization_code");
    }

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

    @Test
    @DisplayName("호출자가 롤백해도 코드는 태워진다")
    void consumeSurvivesCallerRollback() {
        // 교환은 꺼낸 뒤에 만료와 임자를 확인하고, 어긋나면 예외를 던져 트랜잭션을 롤백시킨다.
        // 그 롤백이 이 삭제까지 되돌리면 틀린 요청 하나가 코드를 무효화하지 못하고,
        // 코드는 남은 수명 내내 다시 쓸 수 있는 값으로 살아 있다.
        AuthorizationCode issued = issued();
        adapter.save(issued);

        new TransactionTemplate(transactionManager).execute(status -> {
            adapter.consume(issued.getCode());
            status.setRollbackOnly();
            return null;
        });

        assertThat(rowExists(issued.getCode())).isFalse();
    }

    private boolean rowExists(String code) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM oauth_authorization_code WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
