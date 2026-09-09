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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        @DisplayName("주체 식별자로도 찾는다 — 유형이 다르면 없는 것과 같다")
        void findBySubjectId() {
            Principal principal = Principal.create(new SubjectId("EMP-ESNTL-1"), SubjectType.EMPLOYEE);
            principalAdapter.save(principal);
            flushClear();

            assertThat(principalAdapter.findBySubjectId(SubjectType.EMPLOYEE, new SubjectId("EMP-ESNTL-1")))
                    .isPresent();
            assertThat(principalAdapter.findBySubjectId(SubjectType.EMPLOYEE, new SubjectId("EMP-NONE")))
                    .isEmpty();
            // 유일키는 (subject_type, subject_id) 다. 식별자만 맞고 유형이 다르면 찾히지 않아야 한다.
            assertThat(principalAdapter.findBySubjectId(SubjectType.CUSTOMER, new SubjectId("EMP-ESNTL-1")))
                    .isEmpty();
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

        /**
         * 자격증명은 신원에 매달려야 저장된다 — 스키마의 복합 외래키
         * {@code (principal_id, subject_type) -> identity_principal} 가 그것을 강제한다.
         * 전에는 이 테스트들이 주인 없는 principal_id 를 그냥 넣었다. 엔티티에서 만든 스키마에는
         * 그 외래키가 없어서 통과했을 뿐, 운영 스키마에서는 들어가지 않는 행이었다.
         */
        private PrincipalId givenPrincipal(String subjectId, SubjectType subjectType) {
            Principal principal = principalAdapter.save(
                    Principal.create(new SubjectId(subjectId), subjectType));
            flushClear();
            return principal.getPrincipalId();
        }

        @Test
        @DisplayName("자격증명과 실패 상태가 값 그대로 복원된다")
        void accountRoundTrip() {
            PrincipalId principalId = givenPrincipal("CUST-1", SubjectType.CUSTOMER);

            PasswordAccount account = PasswordAccount.create(
                    principalId, SubjectType.CUSTOMER, "hong@example.com", "{bcrypt}hash");
            passwordAdapter.save(account);
            flushClear();

            PasswordAccount loaded = passwordAdapter.findByLoginId(SubjectType.CUSTOMER, "hong@example.com").orElseThrow();
            assertThat(loaded.getPasswordAccountId()).isEqualTo(account.getPasswordAccountId());
            assertThat(loaded.getPrincipalId()).isEqualTo(principalId);
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
                    givenPrincipal("CUST-2", SubjectType.CUSTOMER),
                    SubjectType.CUSTOMER, "dup@example.com", "{bcrypt}hash"));
            flushClear();

            assertThat(passwordAdapter.existsByLoginId(SubjectType.CUSTOMER, "dup@example.com")).isTrue();
            assertThat(passwordAdapter.existsByLoginId(SubjectType.CUSTOMER, "other@example.com")).isFalse();
            assertThat(passwordAdapter.findByLoginId(SubjectType.CUSTOMER, "other@example.com")).isEmpty();
        }

        @Test
        @DisplayName("같은 login_id 가 realm 별로 따로 저장되고 서로 섞이지 않는다")
        void sameLoginIdPerSubjectType() {
            String loginId = "shared@example.com";
            PrincipalId customer = givenPrincipal("CUST-SHARED", SubjectType.CUSTOMER);
            PrincipalId employee = givenPrincipal("EMP-SHARED", SubjectType.EMPLOYEE);

            passwordAdapter.save(PasswordAccount.create(
                    customer, SubjectType.CUSTOMER, loginId, "{bcrypt}customer"));
            passwordAdapter.save(PasswordAccount.create(
                    employee, SubjectType.EMPLOYEE, loginId, "{bcrypt}employee"));
            flushClear();

            // 유니크는 (subject_type, login_id) 라 두 행이 공존한다. 조회는 유형까지 보고 고른다.
            assertThat(passwordAdapter.findByLoginId(SubjectType.CUSTOMER, loginId).orElseThrow()
                    .getPrincipalId()).isEqualTo(customer);
            assertThat(passwordAdapter.findByLoginId(SubjectType.EMPLOYEE, loginId).orElseThrow()
                    .getPrincipalId()).isEqualTo(employee);
        }

        @Test
        @DisplayName("연속 실패로 걸린 잠금이 되읽힌다")
        void lockStateRoundTrip() {
            Instant now = Instant.parse("2026-01-01T00:00:00Z");
            PasswordAccount account = PasswordAccount.create(
                    givenPrincipal("CUST-3", SubjectType.CUSTOMER),
                    SubjectType.CUSTOMER, "locked-read@example.com", "{bcrypt}hash");
            account.recordFailure(now, 1, Duration.ofMinutes(10));
            passwordAdapter.save(account);
            flushClear();

            PasswordAccount loaded = passwordAdapter.findByLoginId(SubjectType.CUSTOMER, "locked-read@example.com").orElseThrow();
            assertThat(loaded.isLocked(now)).isTrue();
            assertThat(loaded.isLocked(now.plus(Duration.ofMinutes(11)))).isFalse();
        }

        @Test
        @DisplayName("신원과 유형이 어긋난 자격증명은 DB 가 받아주지 않는다")
        void subjectTypeMustMatchPrincipal() {
            // 이 계정의 유형은 CUSTOMER 인데 매달린 신원은 EMPLOYEE 다. 애플리케이션 코드가 그런
            // 조합을 만들지 않지만, 코드의 규율만으로 서 있으면 언젠가 샌다 —
            // 그러면 고객 자격증명으로 직원 realm 토큰이 나간다. 스키마가 막는지 본다.
            PrincipalId employee = givenPrincipal("EMP-MISMATCH", SubjectType.EMPLOYEE);

            assertThatThrownBy(() -> {
                passwordAdapter.save(PasswordAccount.create(
                        employee, SubjectType.CUSTOMER, "mismatch@example.com", "{bcrypt}hash"));
                flushClear();
            }).isInstanceOf(Exception.class)
                    .hasMessageContaining("fk_identity_password_account_principal");
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
            // 신원도 별도 커넥션에서 커밋해 둔다. REQUIRES_NEW 로 도는 쓰기는 이 테스트 트랜잭션을
            // 보지 못하므로, 신원이 아직 커밋 전이면 외래키가 걸린다 — 이 어댑터를 실제로 쓰는
            // 인증 경로에서도 신원은 이미 커밋돼 있다.
            String principalId = insertPrincipalOutsideTx("CUST-COMMIT", SubjectType.CUSTOMER);

            PasswordAccount account = PasswordAccount.create(
                    new PrincipalId(principalId), SubjectType.CUSTOMER, loginId, "{bcrypt}hash");
            account.recordFailure(Instant.parse("2026-01-01T00:00:00Z"), 5, Duration.ofMinutes(10));

            try {
                passwordAdapter.updateLoginState(account);

                assertThat(readFailedAttemptsOutsideTx(loginId)).contains(1);
            } finally {
                // 테스트 트랜잭션 롤백에 휩쓸리지 않는 행이라 직접 지운다. 자격증명이 신원을
                // 참조하므로 순서가 있다.
                deleteOutsideTx(loginId);
                deletePrincipalOutsideTx(principalId);
            }
        }

        /** 테스트 트랜잭션 밖에서 신원을 만들어 커밋한다. */
        private String insertPrincipalOutsideTx(String subjectId, SubjectType subjectType) throws Exception {
            String principalId = UUID.randomUUID().toString();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO identity_principal"
                                 + " (principal_id, subject_id, subject_type, status, created_at, updated_at)"
                                 + " VALUES (?, ?, ?, 'ACTIVE', now(), now())")) {
                ps.setString(1, principalId);
                ps.setString(2, subjectId);
                ps.setString(3, subjectType.name());
                ps.executeUpdate();
            }
            return principalId;
        }

        private void deletePrincipalOutsideTx(String principalId) throws Exception {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM identity_principal WHERE principal_id = ?")) {
                ps.setString(1, principalId);
                ps.executeUpdate();
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
            // 신원은 테스트 트랜잭션 롤백으로 사라지지만, 자격증명이 먼저 지워져야 한다(외래키).
        }
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }
}
