package com.brunosong.identityplatform.auth.service.dataaccess.identity.adapter;

import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalStatus;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 신원(Principal / PasswordAccount) 영속 경로 테스트. 어댑터 → 실제 PostgreSQL 왕복을 본다.
 *
 * <p>보는 지점은 도메인 애그리거트가 값 그대로 복원되는가와, 잠금 상태 갱신이 호출자 트랜잭션과
 * 무관하게 커밋되는가다. 뒤쪽이 이 어댑터의 존재 이유(REQUIRES_NEW)라 별도 커넥션으로 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PrincipalPersistenceAdapter.class, PasswordAccountPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class IdentityPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PrincipalPersistenceAdapter principalAdapter;

    @Autowired
    private PasswordAccountPersistenceAdapter passwordAdapter;

    @Autowired
    private EntityManager em;

    @Autowired
    private DataSource dataSource;

    @Nested
    @DisplayName("신원 저장/복원")
    class PrincipalStore {

        @Test
        @DisplayName("주체 정보가 값 그대로 복원된다")
        void principalRoundTrip() {
            Principal principal = Principal.create(new SubjectId("CUST-UUID-1"), SubjectType.CUSTOMER);
            principalAdapter.save(principal);
            flushClear();

            Principal loaded = principalAdapter.findById(principal.getPrincipalId()).orElseThrow();
            assertThat(loaded.getPrincipalId()).isEqualTo(principal.getPrincipalId());
            assertThat(loaded.getSubjectId().value()).isEqualTo("CUST-UUID-1");
            assertThat(loaded.getSubjectType()).isEqualTo(SubjectType.CUSTOMER);
            assertThat(loaded.getStatus()).isEqualTo(PrincipalStatus.ACTIVE);
            assertThat(loaded.getLastAuthenticatedAt()).isNull();
        }

        @Test
        @DisplayName("주체 식별자로도 찾는다")
        void findBySubjectId() {
            Principal principal = Principal.create(new SubjectId("EMP-ESNTL-1"), SubjectType.EMPLOYEE);
            principalAdapter.save(principal);
            flushClear();

            assertThat(principalAdapter.findBySubjectId(new SubjectId("EMP-ESNTL-1"))).isPresent();
            assertThat(principalAdapter.findBySubjectId(new SubjectId("EMP-NONE"))).isEmpty();
        }

        @Test
        @DisplayName("같은 principalId 로 다시 저장하면 행이 늘지 않고 갱신된다")
        void saveIsUpsert() {
            Principal principal = Principal.create(new SubjectId("CUST-UUID-2"), SubjectType.CUSTOMER);
            principalAdapter.save(principal);
            flushClear();

            principal.markAuthenticated();
            principalAdapter.save(principal);
            flushClear();

            assertThat(countPrincipals("CUST-UUID-2")).isEqualTo(1);
            Principal loaded = principalAdapter.findById(principal.getPrincipalId()).orElseThrow();
            assertThat(loaded.getLastAuthenticatedAt()).isNotNull();
        }

        @Test
        @DisplayName("없는 신원은 비어 있다")
        void missingPrincipal() {
            assertThat(principalAdapter.findById(new PrincipalId("no-such-principal"))).isEmpty();
        }

        private long countPrincipals(String subjectId) {
            return em.createQuery(
                            "SELECT COUNT(p) FROM PrincipalJpaEntity p WHERE p.subjectId = :sid", Long.class)
                    .setParameter("sid", subjectId)
                    .getSingleResult();
        }
    }

    @Nested
    @DisplayName("비밀번호 계정")
    class PasswordAccountStore {

        @Test
        @DisplayName("자격증명과 실패 상태가 값 그대로 복원된다")
        void accountRoundTrip() {
            PasswordAccount account = PasswordAccount.create(
                    new PrincipalId("PRIN-1"), "hong@example.com", "{bcrypt}hash");
            passwordAdapter.save(account);
            flushClear();

            PasswordAccount loaded = passwordAdapter.findByLoginId("hong@example.com").orElseThrow();
            assertThat(loaded.getPasswordAccountId()).isEqualTo(account.getPasswordAccountId());
            assertThat(loaded.getPrincipalId().value()).isEqualTo("PRIN-1");
            assertThat(loaded.getPasswordHash()).isEqualTo("{bcrypt}hash");
            assertThat(loaded.getFailedAttempts()).isZero();
            assertThat(loaded.getLockedUntil()).isNull();
            // timestamp 컬럼은 마이크로초 정밀도라 나노초가 반올림된다. 같은 시각인지만 본다.
            assertThat(loaded.getCreatedAt())
                    .isCloseTo(account.getCreatedAt(), within(1, ChronoUnit.MICROS));
        }

        @Test
        @DisplayName("로그인 ID 중복 여부를 가린다")
        void existsByLoginId() {
            passwordAdapter.save(PasswordAccount.create(
                    new PrincipalId("PRIN-2"), "dup@example.com", "{bcrypt}hash"));
            flushClear();

            assertThat(passwordAdapter.existsByLoginId("dup@example.com")).isTrue();
            assertThat(passwordAdapter.existsByLoginId("other@example.com")).isFalse();
            assertThat(passwordAdapter.findByLoginId("other@example.com")).isEmpty();
        }

        @Test
        @DisplayName("연속 실패로 걸린 잠금이 되읽힌다")
        void lockStateRoundTrip() {
            Instant now = Instant.parse("2026-01-01T00:00:00Z");
            PasswordAccount account = PasswordAccount.create(
                    new PrincipalId("PRIN-3"), "locked-read@example.com", "{bcrypt}hash");
            account.recordFailure(now, 1, Duration.ofMinutes(10));
            passwordAdapter.save(account);
            flushClear();

            PasswordAccount loaded = passwordAdapter.findByLoginId("locked-read@example.com").orElseThrow();
            assertThat(loaded.isLocked(now)).isTrue();
            assertThat(loaded.isLocked(now.plus(Duration.ofMinutes(11)))).isFalse();
        }

        /**
         * 이 어댑터가 REQUIRES_NEW 를 쓰는 이유 자체를 본다. 실패 기록은 인증 트랜잭션이 롤백돼도
         * 남아야 하므로, 호출자 트랜잭션(여기서는 테스트 트랜잭션)이 아직 열려 있는 동안
         * 별도 커넥션에서 이미 보여야 한다.
         */
        @Test
        @DisplayName("잠금 갱신은 호출자 트랜잭션과 무관하게 즉시 커밋된다")
        void loginStateCommitsIndependently() throws Exception {
            String loginId = "locked-commit@example.com";
            PasswordAccount account = PasswordAccount.create(
                    new PrincipalId("PRIN-4"), loginId, "{bcrypt}hash");
            account.recordFailure(Instant.parse("2026-01-01T00:00:00Z"), 5, Duration.ofMinutes(10));

            try {
                passwordAdapter.updateLoginState(account);

                assertThat(readFailedAttemptsOutsideTx(loginId)).contains(1);
            } finally {
                // 테스트 트랜잭션 롤백에 휩쓸리지 않는 행이라 직접 지운다.
                deleteOutsideTx(loginId);
            }
        }

        private Optional<Integer> readFailedAttemptsOutsideTx(String loginId) throws Exception {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT failed_attempts FROM identity_password_account WHERE login_id = ?")) {
                ps.setString(1, loginId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
                }
            }
        }

        private void deleteOutsideTx(String loginId) throws Exception {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM identity_password_account WHERE login_id = ?")) {
                ps.setString(1, loginId);
                ps.executeUpdate();
            }
        }
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }
}
