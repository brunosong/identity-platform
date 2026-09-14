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

/**
 * 가입용 OTP 발송 요청 — 로그인용과 <b>조건이 정반대</b>인 것이 이 테스트의 전부다.
 *
 * <p>등록된 주소에 가입 코드가 나가지 않는 것이 "이미 가입된 이메일입니다" 응답을 없앤다.
 * 그 응답이 있으면 주소를 넣어보는 것만으로 누가 가입돼 있는지 훑을 수 있다.
 */
class RequestRegistrationOtpServiceTest {

    private static final String REGISTERED = "already@example.com";
    private static final String FRESH = "newcomer@example.com";

    private FakeEmailAccountRepository emailAccountRepo;
    private FakeEmailOtpStore otpStore;
    private RecordingSender sender;
    private RequestRegistrationOtpService service;

    @BeforeEach
    void setUp() {
        emailAccountRepo = new FakeEmailAccountRepository();
        emailAccountRepo.save(EmailAccount.unverified(PrincipalId.newId(), Realm.PORTAL, REGISTERED));
        otpStore = new FakeEmailOtpStore();
        sender = new RecordingSender();
        service = new RequestRegistrationOtpService(
                emailAccountRepo,
                new EmailOtpIssuer(otpStore, new FakePasswordEncoder(), new MockEnvironment(), provider(sender)));
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 인증번호를 보낸다")
    void sendsCodeForFreshEmail() {
        service.request(Realm.PORTAL, FRESH);

        assertThat(otpStore.saved).hasSize(1);
        assertThat(sender.sentCodes).hasSize(1);
        assertThat(sender.sentCodes.get(0)).hasSize(6).containsOnlyDigits();
    }

    @Test
    @DisplayName("이미 가입된 이메일에는 보내지 않는다 — 그래서 '이미 가입됨' 응답이 필요 없다")
    void registeredEmailGetsNothing() {
        service.request(Realm.PORTAL, REGISTERED);

        assertThat(otpStore.saved).isEmpty();
        assertThat(sender.sentCodes).isEmpty();
    }

    @Test
    @DisplayName("같은 주소라도 다른 realm 으로는 아직 미가입이라 보낸다")
    void sameEmailOtherRealmIsFresh() {
        service.request(Realm.ADMIN, REGISTERED);

        assertThat(sender.sentCodes).hasSize(1);
    }

    @Test
    @DisplayName("쿨다운 안에 다시 요청하면 재발송하지 않는다")
    void resendWithinCooldownIsSkipped() {
        service.request(Realm.PORTAL, FRESH);
        service.request(Realm.PORTAL, FRESH);

        assertThat(sender.sentCodes).hasSize(1);
    }

    private static final class RecordingSender implements OtpEmailSenderPort {
        final List<String> sentCodes = new ArrayList<>();

        @Override
        public void send(String email, String code) {
            sentCodes.add(code);
        }
    }
}
