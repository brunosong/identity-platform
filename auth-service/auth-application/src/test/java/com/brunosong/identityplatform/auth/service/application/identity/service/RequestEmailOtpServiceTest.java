package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.OtpEmailSenderPort;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailOtpStore;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordEncoder;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로그인용 OTP 발송 요청. 미등록 이메일 무응답(열거 방지)과 재발송 쿨다운(메일 폭탄 방지)이 핵심이다.
 *
 * <p>코드 생성·저장·발송은 {@link EmailOtpIssuer} 가 갖는다(가입용과 공유). 여기서 함께 검증한다 —
 * 발송기를 따로 떼어 두면 "등록 여부 판단"과 "실제로 나갔는가"가 갈려서 읽기 어려워진다.
 */
class RequestEmailOtpServiceTest {

    private static final String EMAIL = "user@example.com";

    private FakeEmailAccountRepository emailAccountRepo;
    private FakeEmailOtpStore otpStore;
    private RecordingSender sender;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        emailAccountRepo = new FakeEmailAccountRepository();
        emailAccountRepo.save(EmailAccount.unverified(PrincipalId.newId(), Realm.ADMIN, EMAIL));
        otpStore = new FakeEmailOtpStore();
        sender = new RecordingSender();
        environment = new MockEnvironment();
    }

    private RequestEmailOtpService service() {
        return new RequestEmailOtpService(emailAccountRepo, issuer(sender));
    }

    private EmailOtpIssuer issuer(OtpEmailSenderPort emailSender) {
        return new EmailOtpIssuer(otpStore, new FakePasswordEncoder(), environment, provider(emailSender));
    }

    @Test
    @DisplayName("등록된 이메일이면 6자리 인증번호를 저장하고 발송한다")
    void sendsOtpForRegisteredEmail() {
        service().request(Realm.ADMIN, EMAIL);

        assertThat(otpStore.saved).hasSize(1);
        assertThat(sender.sentCodes).hasSize(1);
        assertThat(sender.sentCodes.get(0)).hasSize(6).containsOnlyDigits();
    }

    @Test
    @DisplayName("미등록 이메일은 조용히 무시한다(계정 열거 방지)")
    void unknownEmailIsSilentlyIgnored() {
        service().request(Realm.ADMIN, "nobody@example.com");

        assertThat(otpStore.saved).isEmpty();
        assertThat(sender.sentCodes).isEmpty();
    }

    @Test
    @DisplayName("같은 주소라도 다른 realm 으로는 보내지 않는다")
    void otherRealmIsNotRegistered() {
        service().request(Realm.PORTAL, EMAIL);

        assertThat(otpStore.saved).isEmpty();
        assertThat(sender.sentCodes).isEmpty();
    }

    @Test
    @DisplayName("쿨다운 안에 다시 요청하면 재발송하지 않는다")
    void resendWithinCooldownIsSkipped() {
        RequestEmailOtpService service = service();
        service.request(Realm.ADMIN, EMAIL);
        service.request(Realm.ADMIN, EMAIL);

        assertThat(otpStore.saved).hasSize(1);
        assertThat(sender.sentCodes).hasSize(1);
    }

    @Test
    @DisplayName("local 프로파일에서는 고정 인증번호를 저장하고 메일은 보내지 않는다")
    void localProfileUsesFixedCodeWithoutSending() {
        environment.setActiveProfiles("local");

        service().request(Realm.ADMIN, EMAIL);

        assertThat(otpStore.saved).hasSize(1);
        assertThat(sender.sentCodes).isEmpty();
        assertThat(new FakePasswordEncoder().matches("123456", otpStore.saved.get(0).getCodeHash())).isTrue();
    }

    @Test
    @DisplayName("발송 어댑터가 없으면 요청이 실패한다")
    void withoutSenderFails() {
        RequestEmailOtpService noSender = new RequestEmailOtpService(emailAccountRepo, issuer(null));

        assertThatThrownBy(() -> noSender.request(Realm.ADMIN, EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OtpEmailSenderPort");
    }

    private static final class RecordingSender implements OtpEmailSenderPort {
        final List<String> sentCodes = new ArrayList<>();

        @Override
        public void send(String email, String code) {
            sentCodes.add(code);
        }
    }
}
