package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordEncoder;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeTokenIssuer;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.RecordingEventPublisher;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 아이디/비밀번호 로그인. 계정 열거 방지(동일 메시지)와 연속 실패 잠금이 핵심이라 그 두 가지를 본다.
 */
class AuthenticateWithPasswordServiceTest {

    private static final String LOGIN_ID = "gildong";
    private static final String PASSWORD = "pw1234!";
    private static final String GENERIC_FAIL = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private FakePrincipalRepository principalRepo;
    private FakePasswordAccountRepository accountRepo;
    private RecordingEventPublisher eventPublisher;
    private AuthenticateWithPasswordService service;

    private Principal principal;

    @BeforeEach
    void setUp() {
        principalRepo = new FakePrincipalRepository();
        accountRepo = new FakePasswordAccountRepository();
        eventPublisher = new RecordingEventPublisher();
        FakePasswordEncoder encoder = new FakePasswordEncoder();

        principal = principalRepo.seed("customer-uuid-1", SubjectType.CUSTOMER);
        accountRepo.save(PasswordAccount.create(
                principal.getPrincipalId(), SubjectType.CUSTOMER, LOGIN_ID, encoder.encode(PASSWORD)));

        service = new AuthenticateWithPasswordService(
                principalRepo,
                new PasswordCredentialVerifier(accountRepo, encoder),
                new AuthenticationCompletion(principalRepo, eventPublisher,
                        new TokenIssuance(provider(new FakeTokenIssuer()))));
    }

    @Test
    @DisplayName("비밀번호가 맞으면 토큰이 발급되고 인증 이벤트가 나간다")
    void authenticateIssuesTokenAndPublishesEvent() {
        AuthenticationResult result = service.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, PASSWORD));

        assertThat(result.subjectId()).isEqualTo("customer-uuid-1");
        assertThat(result.subjectType()).isEqualTo(SubjectType.CUSTOMER);
        assertThat(result.tokens().accessToken()).isEqualTo("access:customer-uuid-1");
        assertThat(eventPublisher.published).hasSize(1);
        assertThat(eventPublisher.published.get(0).subjectId()).isEqualTo("customer-uuid-1");
    }

    @Test
    @DisplayName("로그인 성공은 Principal 의 최종 인증 시각을 남긴다")
    void authenticateMarksPrincipal() {
        service.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, PASSWORD));

        assertThat(principal.getLastAuthenticatedAt()).isNotNull();
    }

    @Test
    @DisplayName("없는 아이디와 틀린 비밀번호는 같은 메시지로 실패한다(계정 열거 방지)")
    void unknownIdAndWrongPasswordShareMessage() {
        assertThatThrownBy(() -> service.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, "nobody", PASSWORD)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(GENERIC_FAIL);

        assertThatThrownBy(() -> service.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, "wrong")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(GENERIC_FAIL);
    }

    @Test
    @DisplayName("같은 아이디·비밀번호라도 다른 realm 으로는 로그인되지 않는다")
    void credentialsDoNotCrossRealms() {
        // 자격증명은 정확하지만 직원 realm 에는 이 계정이 없다. 조회가 유형으로 좁혀지지 않으면
        // 고객 자격증명으로 직원 토큰이 발급된다 — 그것을 막는 것이 이 검증의 목적이다.
        assertThatThrownBy(() -> service.authenticate(
                new PasswordAuthCommand(SubjectType.EMPLOYEE, LOGIN_ID, PASSWORD)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(GENERIC_FAIL);
    }

    @Test
    @DisplayName("같은 loginId 가 realm 마다 따로 설 수 있다")
    void sameLoginIdCanExistInBothRealms() {
        Principal employee = principalRepo.seed("employee-esntl-1", SubjectType.EMPLOYEE);
        accountRepo.save(PasswordAccount.create(employee.getPrincipalId(), SubjectType.EMPLOYEE,
                LOGIN_ID, new FakePasswordEncoder().encode("other-pw")));

        AuthenticationResult customer = service.authenticate(
                new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, PASSWORD));

        // 나중에 저장한 직원 계정이 고객 계정을 덮어쓰지 않았다 — 서로 다른 신원이다.
        assertThat(customer.subjectId()).isEqualTo("customer-uuid-1");
        assertThat(customer.subjectType()).isEqualTo(SubjectType.CUSTOMER);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 실패 상태가 저장된다(인증 트랜잭션과 별개로 남아야 함)")
    void wrongPasswordPersistsFailure() {
        assertThatThrownBy(() -> service.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, "wrong")))
                .isInstanceOf(AuthenticationFailedException.class);

        assertThat(accountRepo.loginStateUpdates).isEqualTo(1);
        assertThat(accountRepo.findByLoginId(SubjectType.CUSTOMER, LOGIN_ID).orElseThrow().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("연속 5회 실패하면 계정이 잠기고 이후엔 올바른 비밀번호도 거부된다")
    void locksAfterFiveFailures() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, "wrong")))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        assertThatThrownBy(() -> service.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, PASSWORD)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("일시적으로 잠겼습니다");
    }

    @Test
    @DisplayName("실패가 쌓여 있어도 성공하면 실패 상태가 초기화된다")
    void successResetsFailureState() {
        assertThatThrownBy(() -> service.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, "wrong")))
                .isInstanceOf(AuthenticationFailedException.class);

        service.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, PASSWORD));

        PasswordAccount account = accountRepo.findByLoginId(SubjectType.CUSTOMER, LOGIN_ID).orElseThrow();
        assertThat(account.getFailedAttempts()).isZero();
        assertThat(account.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("토큰 발급기가 없는 호스트에서는 로그인할 수 없다")
    void withoutTokenIssuerFails() {
        AuthenticateWithPasswordService noIssuer = new AuthenticateWithPasswordService(
                principalRepo,
                new PasswordCredentialVerifier(accountRepo, new FakePasswordEncoder()),
                new AuthenticationCompletion(principalRepo, eventPublisher,
                        new TokenIssuance(provider((TokenIssuerPort) null))));

        assertThatThrownBy(() -> noIssuer.authenticate(new PasswordAuthCommand(SubjectType.CUSTOMER, LOGIN_ID, PASSWORD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TokenIssuerPort");
    }
}
