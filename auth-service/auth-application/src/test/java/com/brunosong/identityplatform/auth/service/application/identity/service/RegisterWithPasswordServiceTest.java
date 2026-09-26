package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.event.SubjectRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.RecordingEventPublisher;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordEncoder;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalProfileRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailOtpStore;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.provider;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.RecordingSubjectRegisteredPublisher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 가입 — 아이디 중복 차단, 주체 등록 이벤트 발행, 기존 주체 재사용을 본다.
 *
 * <p>프로필을 실제로 만드는 것은 이벤트를 받는 customer 리스너라 여기서는 발행까지만 본다.
 */
class RegisterWithPasswordServiceTest {

    private FakePrincipalRepository principalRepo;
    private FakePrincipalProfileRepository profileRepo;
    private FakePasswordAccountRepository accountRepo;
    private FakeEmailAccountRepository emailAccountRepo;
    private RecordingSubjectRegisteredPublisher registeredPublisher;
    private RecordingEventPublisher authenticatedPublisher;
    private FakeEmailOtpStore otpStore;
    private RegisterWithPasswordService service;

    @BeforeEach
    void setUp() {
        principalRepo = new FakePrincipalRepository();
        accountRepo = new FakePasswordAccountRepository();
        emailAccountRepo = new FakeEmailAccountRepository();
        registeredPublisher = new RecordingSubjectRegisteredPublisher();
        profileRepo = new FakePrincipalProfileRepository();
        authenticatedPublisher = new RecordingEventPublisher();
        otpStore = new FakeEmailOtpStore();
        service = new RegisterWithPasswordService(
                principalRepo, profileRepo, accountRepo, emailAccountRepo, new FakePasswordEncoder(),
                registeredPublisher, new EmailOtpVerifier(otpStore, new FakePasswordEncoder()));
    }

    /** 그 이메일로 가입용 인증번호를 보낸 뒤 받은 번호를 적은 명령. 대부분의 테스트가 이 상태에서 시작한다. */
    private RegisterWithPasswordCommand command(String loginId, String email) {
        issueCode(email);
        return new RegisterWithPasswordCommand(
                Realm.PORTAL, email, "홍길동", "01012345678", loginId, "pw1234!", "123456");
    }

    /** 발송 단계를 거친 것과 같은 상태를 만든다. local 프로파일은 고정코드 123456 을 쓴다. */
    private void issueCode(String email) {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        new EmailOtpIssuer(otpStore, new FakePasswordEncoder(), local, provider((to, code) -> { }))
                .issue(email);
    }

    @Test
    @DisplayName("인증번호가 없거나 틀리면 아무것도 만들지 않는다")
    void requiresVerificationCode() {
        // 비밀번호 가입도 이메일의 주인임을 먼저 증명해야 한다. 남의 주소로 계정을 만들 수 없다.
        assertThatThrownBy(() -> service.register(new RegisterWithPasswordCommand(
                Realm.PORTAL, "nocode@example.com", "홍길동", null, "nocode@example.com", "pw1234!", "123456")))
                .isInstanceOf(AuthenticationFailedException.class);

        issueCode("wrong@example.com");
        assertThatThrownBy(() -> service.register(new RegisterWithPasswordCommand(
                Realm.PORTAL, "wrong@example.com", "홍길동", null, "wrong@example.com", "pw1234!", "000000")))
                .isInstanceOf(AuthenticationFailedException.class);

        assertThat(principalRepo.byId).isEmpty();
        assertThat(registeredPublisher.published).isEmpty();
    }

    @Test
    @DisplayName("신규 가입은 주체 등록을 알리고 Principal 과 비밀번호 계정을 남긴다")
    void registerPublishesSubjectRegistered() {
        String principalId = service.register(command("gildong", "gildong@example.com")).principalId().value();

        assertThat(registeredPublisher.published).hasSize(1);
        assertThat(principalRepo.byId).containsKey(principalId);
        assertThat(accountRepo.findByLoginId(Realm.PORTAL, "gildong")).isPresent();
    }

    @Test
    @DisplayName("subjectId 는 auth 가 채번해서 이벤트에 실어 보낸다")
    void authMintsSubjectId() {
        String principalId = service.register(command("gildong", "gildong@example.com")).principalId().value();

        assertThat(principalRepo.byId.get(principalId).getSubjectId().value())
                .isEqualTo(registeredPublisher.published.get(0).subjectId());
    }

    @Test
    @DisplayName("프로필을 만드는 데 필요한 값이 이벤트에 실린다")
    void eventCarriesProfileFields() {
        service.register(command("gildong", "gildong@example.com"));

        SubjectRegisteredEvent event = registeredPublisher.published.get(0);
        assertThat(event.realm()).isEqualTo(Realm.PORTAL);
        assertThat(event.email()).isEqualTo("gildong@example.com");
        assertThat(event.name()).isEqualTo("홍길동");
        assertThat(event.phoneNumber()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("비밀번호는 원문이 아니라 인코딩되어 저장된다")
    void passwordIsEncoded() {
        service.register(command("gildong", "gildong@example.com"));

        assertThat(accountRepo.findByLoginId(Realm.PORTAL, "gildong").orElseThrow().getPasswordHash())
                .isEqualTo("hash:pw1234!")
                .isNotEqualTo("pw1234!");
    }

    @Test
    @DisplayName("이미 있는 아이디로는 가입할 수 없다")
    void duplicateLoginIdRejected() {
        service.register(command("gildong", "gildong@example.com"));

        assertThatThrownBy(() -> service.register(command("gildong", "other@example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 아이디");
    }

    @Test
    @DisplayName("이미 가입된 이메일로는 비밀번호 가입을 받지 않는다(계정 탈취 방지)")
    void existingEmailIsRefused() {
        // 구글이나 인증번호로 이미 가입해 둔 사람이다. 받아 주면 그 신원에 비밀번호가 붙는다.
        // 가입용 번호는 가입된 주소로 나가지 않지만, 번호를 받은 뒤 사이에 가입된 경우를 여기서 막는다.
        Principal existing = principalRepo.seed("subject-1", Realm.PORTAL);
        emailAccountRepo.save(EmailAccount.verified(existing.getPrincipalId(), Realm.PORTAL, "same@example.com"));

        assertThatThrownBy(() -> service.register(command("gildong2", "same@example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 가입된 이메일");
        assertThat(principalRepo.byId).hasSize(1);
        assertThat(accountRepo.findByLoginId(Realm.PORTAL, "gildong2")).isEmpty();
    }

    @Test
    @DisplayName("가입하면 이메일로 신원을 되찾을 수 있게 이메일 계정을 남긴다")
    void emailAccountIsCreated() {
        String principalId = service.register(command("gildong", "gildong@example.com")).principalId().value();

        assertThat(emailAccountRepo.findByEmail(Realm.PORTAL, "gildong@example.com"))
                .get()
                .extracting(a -> a.getPrincipalId().value())
                .isEqualTo(principalId);
    }

    @Test
    @DisplayName("비밀번호로 가입해도 이메일은 확인된 주소다. 인증번호를 받아냈기 때문이다")
    void emailAccountIsVerified() {
        service.register(command("gildong", "gildong@example.com"));

        assertThat(emailAccountRepo.findByEmail(Realm.PORTAL, "gildong@example.com").orElseThrow().isVerified())
                .isTrue();
    }

    @Test
    @DisplayName("같은 이메일이라도 realm 이 다르면 서로 다른 신원이다")
    void sameEmailDifferentRealm() {
        service.register(command("gildong", "same@example.com"));

        assertThat(emailAccountRepo.findByEmail(Realm.ADMIN, "same@example.com")).isEmpty();
    }

    @Test
    @DisplayName("가입은 로그인이 아니다. 인증 시각과 인증 이벤트를 남기지 않는다")
    void registerDoesNotAuthenticate() {
        // 가입 뒤 로그인할지는 입구가 정한다. 가입만 하는 입구로 온 사람에게 로그인 기록이 남으면 안 된다.
        String principalId = service.register(command("gildong", "gildong@example.com")).principalId().value();

        Principal principal = principalRepo.byId.get(principalId);
        assertThat(principal.getLastAuthenticatedAt()).isNull();
        assertThat(authenticatedPublisher.published).isEmpty();
    }

    @Test
    @DisplayName("가입 폼의 이름·전화번호를 신원 프로필로 저장한다")
    void savesPrincipalProfile() {
        String principalId = service.register(command("hong", "hong@example.com")).principalId().value();

        assertThat(profileRepo.byPrincipalId).containsKey(principalId);
        var profile = profileRepo.byPrincipalId.get(principalId);
        assertThat(profile.getName()).isEqualTo("홍길동");
        assertThat(profile.getPhoneNumber()).isEqualTo("01012345678");
    }
}
