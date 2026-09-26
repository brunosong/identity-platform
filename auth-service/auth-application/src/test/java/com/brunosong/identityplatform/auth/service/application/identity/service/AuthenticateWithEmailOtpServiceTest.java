package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.EmailOtpAuthCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailOtpChallenge;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailOtpStore;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordEncoder;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.RecordingEventPublisher;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이메일 OTP 로그인. 만료/시도횟수/재사용 차단이 인증 안전성의 핵심이라 그 경계를 본다.
 */
class AuthenticateWithEmailOtpServiceTest {


    private static final String EMAIL = "user@example.com";
    private static final String CODE = "123456";

    private FakeEmailAccountRepository emailAccountRepo;
    private FakePrincipalRepository principalRepo;
    private FakeEmailOtpStore otpStore;
    private FakePasswordEncoder encoder;
    private RecordingEventPublisher eventPublisher;
    private AuthenticateWithEmailOtpService service;

    @BeforeEach
    void setUp() {
        emailAccountRepo = new FakeEmailAccountRepository();
        principalRepo = new FakePrincipalRepository();
        otpStore = new FakeEmailOtpStore();
        encoder = new FakePasswordEncoder();
        eventPublisher = new RecordingEventPublisher();

        Principal principal = principalRepo.seed("esntl-1", Realm.ADMIN);
        // 비밀번호 가입으로 들어온 것과 같은 상태 — 아직 확인되지 않은 주소다.
        emailAccountRepo.save(EmailAccount.unverified(principal.getPrincipalId(), Realm.ADMIN, EMAIL));

        service = new AuthenticateWithEmailOtpService(
                emailAccountRepo, principalRepo, new EmailOtpVerifier(otpStore, encoder),
                new AuthenticationCompletion(principalRepo, eventPublisher));
    }

    private EmailOtpChallenge issueOtp() {
        Instant now = Instant.now();
        return otpStore.save(EmailOtpChallenge.issue(EMAIL, encoder.encode(CODE), now, now.plus(Duration.ofMinutes(5))));
    }

    @Test
    @DisplayName("유효한 인증번호면 로그인되고 그 챌린지는 사용 처리된다")
    void validCodeAuthenticates() {
        EmailOtpChallenge otp = issueOtp();

        AuthenticatedSubject result = service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, CODE));

        assertThat(result.subjectId()).isEqualTo("esntl-1");
        assertThat(otp.isUsed()).isTrue();
        assertThat(eventPublisher.published).hasSize(1);
    }

    @Test
    @DisplayName("한 번 쓴 인증번호로는 다시 로그인할 수 없다")
    void usedCodeCannotBeReplayed() {
        issueOtp();
        service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, CODE));

        assertThatThrownBy(() -> service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, CODE)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("유효하지 않습니다");
    }

    @Test
    @DisplayName("만료된 인증번호는 거부된다")
    void expiredCodeRejected() {
        Instant past = Instant.now().minus(Duration.ofMinutes(10));
        otpStore.save(EmailOtpChallenge.issue(EMAIL, encoder.encode(CODE), past, past.plus(Duration.ofMinutes(5))));

        assertThatThrownBy(() -> service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, CODE)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("만료");
    }

    @Test
    @DisplayName("틀린 인증번호는 시도 횟수를 올리고 저장한다")
    void wrongCodeRecordsAttempt() {
        EmailOtpChallenge otp = issueOtp();

        assertThatThrownBy(() -> service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, "000000")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("일치하지 않습니다");

        assertThat(otp.getAttemptCount()).isEqualTo(1);
        assertThat(otp.isUsed()).isFalse();
    }

    @Test
    @DisplayName("5회 틀리면 그 챌린지는 잠기고 올바른 인증번호도 받지 않는다")
    void blockedAfterFiveAttempts() {
        issueOtp();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, "000000")))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        assertThatThrownBy(() -> service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, CODE)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("시도 횟수를 초과");
    }

    @Test
    @DisplayName("등록되지 않은 이메일은 인증번호 유무와 무관하게 같은 실패로 끝난다(열거 방지)")
    void unknownEmailFails() {
        assertThatThrownBy(() -> service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, "nobody@example.com", CODE)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("인증에 실패했습니다.");
    }

    @Test
    @DisplayName("발급된 인증번호가 없으면 재요청을 안내한다")
    void noChallengeFails() {
        assertThatThrownBy(() -> service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, CODE)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("다시 요청");
    }

    @Test
    @DisplayName("코드 불일치는 시도 횟수를 별도 트랜잭션으로 남긴다")
    void failedAttemptIsRecordedOutOfBand() {
        // 검증 실패는 예외로 끝나고 그 예외가 인증 트랜잭션을 되돌린다. 시도 횟수를 같은 트랜잭션에서
        // 올리면 증가분도 함께 사라져 잠금이 영원히 걸리지 않는다 — 무제한 대입이 가능해진다.
        issueOtp();

        assertThatThrownBy(() -> service.withEmailOtp(
                new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, "000000")))
                .isInstanceOf(AuthenticationFailedException.class);

        assertThat(otpStore.failedAttemptRecords).isEqualTo(1);
        assertThat(otpStore.findLatestUnused(EMAIL).orElseThrow().getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인에 성공하면 그 이메일이 확인됨으로 올라간다")
    void successfulLoginVerifiesTheEmail() {
        assertThat(emailAccountRepo.findByEmail(Realm.ADMIN, EMAIL))
                .get().returns(false, EmailAccount::isVerified);

        issueOtp();
        service.withEmailOtp(new EmailOtpAuthCommand(Realm.ADMIN, EMAIL, CODE));

        // 코드를 받아냈다는 것이 곧 그 주소의 주인이라는 증거다.
        assertThat(emailAccountRepo.findByEmail(Realm.ADMIN, EMAIL))
                .get().returns(true, EmailAccount::isVerified);
    }
}
