package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithEmailCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.OtpEmailSenderPort;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailOtpChallenge;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailOtpStore;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordEncoder;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalProfileRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.RecordingSubjectRegisteredPublisher;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비밀번호 없는 가입.
 *
 * <p>확인하려는 것은 셋이다 — 인증번호를 통과해야만 신원이 생기고, 만들어진 신원에는 비밀번호
 * 계정이 붙지 않으며, 등록을 알리는 이벤트는 비밀번호 가입과 똑같이 나간다(역할 부여가 그
 * 이벤트에 달려 있다).
 */
class RegisterWithEmailServiceTest {

    private static final String EMAIL = "new@example.com";
    private static final String CODE = "123456";

    private FakePrincipalRepository principals;
    private FakePrincipalProfileRepository profiles;
    private FakeEmailAccountRepository emailAccounts;
    private FakeEmailOtpStore otpStore;
    private RecordingSubjectRegisteredPublisher registeredPublisher;
    private RegisterWithEmailService service;

    @BeforeEach
    void setUp() {
        principals = new FakePrincipalRepository();
        emailAccounts = new FakeEmailAccountRepository();
        otpStore = new FakeEmailOtpStore();
        registeredPublisher = new RecordingSubjectRegisteredPublisher();
        profiles = new FakePrincipalProfileRepository();
        service = new RegisterWithEmailService(
                principals, profiles, emailAccounts,
                new EmailOtpVerifier(otpStore, new FakePasswordEncoder()),
                registeredPublisher);
    }

    /** 발송 단계를 거친 것과 같은 상태를 만든다. */
    private void issueCode(String email, String code) {
        new EmailOtpIssuer(otpStore, new FakePasswordEncoder(), localEnvironment(), provider(noSender()))
                .issue(email);
        assertThat(new FakePasswordEncoder().matches(code, otpStore.saved.get(otpStore.saved.size() - 1).getCodeHash()))
                .isTrue();
    }

    private static MockEnvironment localEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");   // 고정코드 123456
        return environment;
    }

    private static OtpEmailSenderPort noSender() {
        return (email, code) -> {
        };
    }

    private RegisterWithEmailCommand command() {
        return new RegisterWithEmailCommand(Realm.PORTAL, EMAIL, "홍길동", "010-0000-0000", CODE);
    }

    @Test
    @DisplayName("인증번호가 맞으면 신원과 이메일 계정을 만든다")
    void createsPrincipalAndEmailAccount() {
        issueCode(EMAIL, CODE);

        String principalId = service.register(command()).principalId().value();

        assertThat(principalId).isNotBlank();
        assertThat(principals.byId).containsKey(principalId);
        assertThat(emailAccounts.findByEmail(Realm.PORTAL, EMAIL)).isPresent();
    }

    @Test
    @DisplayName("이렇게 만든 이메일 계정은 확인된 것이다 — 인증번호를 받아냈기 때문이다")
    void createdEmailAccountIsVerified() {
        issueCode(EMAIL, CODE);

        service.register(command());

        assertThat(emailAccounts.findByEmail(Realm.PORTAL, EMAIL))
                .get().returns(true, EmailAccount::isVerified);
    }

    @Test
    @DisplayName("등록 이벤트를 발행한다 — 역할 부여가 여기에 달려 있다")
    void publishesSubjectRegisteredEvent() {
        issueCode(EMAIL, CODE);

        service.register(command());

        assertThat(registeredPublisher.published).hasSize(1);
        assertThat(registeredPublisher.published.get(0).realm()).isEqualTo(Realm.PORTAL);
        assertThat(registeredPublisher.published.get(0).email()).isEqualTo(EMAIL);
        assertThat(registeredPublisher.published.get(0).name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("인증번호가 없으면 아무것도 만들지 않는다")
    void withoutChallengeNothingIsCreated() {
        assertThatThrownBy(() -> service.register(command()))
                .isInstanceOf(AuthenticationFailedException.class);

        assertThat(principals.byId).isEmpty();
        assertThat(registeredPublisher.published).isEmpty();
    }

    @Test
    @DisplayName("인증번호가 틀리면 신원이 생기지 않고 실패가 기록된다")
    void wrongCodeIsRejectedAndCounted() {
        issueCode(EMAIL, CODE);

        assertThatThrownBy(() -> service.register(new RegisterWithEmailCommand(
                Realm.PORTAL, EMAIL, "홍길동", null, "000000")))
                .isInstanceOf(AuthenticationFailedException.class);

        assertThat(principals.byId).isEmpty();
        // 실패 기록은 별도 트랜잭션으로 남아야 한다 — 같이 롤백되면 잠금이 영원히 걸리지 않는다.
        assertThat(otpStore.failedAttemptRecords).isEqualTo(1);
    }

    @Test
    @DisplayName("만료된 인증번호는 거부한다")
    void expiredCodeIsRejected() {
        Instant past = Instant.now().minus(Duration.ofMinutes(10));
        otpStore.save(EmailOtpChallenge.issue(
                EMAIL, new FakePasswordEncoder().encode(CODE), past, past.plus(Duration.ofMinutes(5))));

        assertThatThrownBy(() -> service.register(command()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("만료");

        assertThat(principals.byId).isEmpty();
    }

    @Test
    @DisplayName("한 번 쓴 인증번호로 또 가입할 수 없다")
    void codeCannotBeReused() {
        issueCode(EMAIL, CODE);
        service.register(command());

        assertThatThrownBy(() -> service.register(new RegisterWithEmailCommand(
                Realm.PORTAL, "other@example.com", "다른사람", null, CODE)))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("이미 그 주소의 신원이 있으면 새로 만들지 않고 그것을 돌려준다")
    void existingIdentityIsReturnedAsIs() {
        var existing = principals.seed("subject-1", Realm.PORTAL);
        emailAccounts.save(EmailAccount.verified(existing.getPrincipalId(), Realm.PORTAL, EMAIL));
        issueCode(EMAIL, CODE);

        String principalId = service.register(command()).principalId().value();

        assertThat(principalId).isEqualTo(existing.getPrincipalId().value());
        assertThat(principals.byId).hasSize(1);
        assertThat(registeredPublisher.published).isEmpty();
    }

    @Test
    @DisplayName("비밀번호 계정은 만들지 않는다 — 이 사람은 OTP 로만 들어온다")
    void noPasswordAccountIsCreated() {
        issueCode(EMAIL, CODE);

        service.register(command());

        // 이 서비스는 PasswordAccountRepository 를 아예 의존하지 않는다. 생성자가 그것을 증명한다.
        List<String> collaborators = new ArrayList<>();
        for (var parameter : RegisterWithEmailService.class.getDeclaredConstructors()[0].getParameterTypes()) {
            collaborators.add(parameter.getSimpleName());
        }
        assertThat(collaborators).doesNotContain("PasswordAccountRepository");
    }

    @Test
    @DisplayName("가입 폼의 이름·전화번호를 신원 프로필로 저장한다")
    void savesPrincipalProfile() {
        issueCode(EMAIL, CODE);

        String principalId = service.register(command()).principalId().value();

        assertThat(profiles.byPrincipalId).containsKey(principalId);
        assertThat(profiles.byPrincipalId.get(principalId).getName()).isEqualTo("홍길동");
        assertThat(profiles.byPrincipalId.get(principalId).getPhoneNumber()).isEqualTo("010-0000-0000");
    }

    @Test
    @DisplayName("인증번호가 틀리면 프로필도 남지 않는다")
    void wrongCodeLeavesNoProfile() {
        issueCode(EMAIL, CODE);

        assertThatThrownBy(() -> service.register(new RegisterWithEmailCommand(
                Realm.PORTAL, EMAIL, "홍길동", null, "000000")))
                .isInstanceOf(AuthenticationFailedException.class);

        assertThat(profiles.byPrincipalId).isEmpty();
    }
}
