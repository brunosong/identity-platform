package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.event.SubjectRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.RecordingEventPublisher;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordEncoder;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalProfileRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailAccountRepository;
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
    private RegisterWithPasswordService service;

    @BeforeEach
    void setUp() {
        principalRepo = new FakePrincipalRepository();
        accountRepo = new FakePasswordAccountRepository();
        emailAccountRepo = new FakeEmailAccountRepository();
        registeredPublisher = new RecordingSubjectRegisteredPublisher();
        profileRepo = new FakePrincipalProfileRepository();
        authenticatedPublisher = new RecordingEventPublisher();
        service = new RegisterWithPasswordService(
                principalRepo, profileRepo, accountRepo, emailAccountRepo, new FakePasswordEncoder(),
                registeredPublisher, new AuthenticationCompletion(principalRepo, authenticatedPublisher));
    }

    private RegisterWithPasswordCommand command(String loginId, String email) {
        return new RegisterWithPasswordCommand(
                Realm.PORTAL, email, "홍길동", "01012345678", loginId, "pw1234!");
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
        // 받아 주면 그 신원에 비밀번호가 붙는다. 이 폼은 이메일 소유를 확인하지 않으므로, 남의 이메일을
        // 적은 사람이 자기 비밀번호로 그 사람의 계정에 들어가게 된다.
        service.register(command("gildong", "same@example.com"));

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
    @DisplayName("같은 이메일이라도 realm 이 다르면 서로 다른 신원이다")
    void sameEmailDifferentRealm() {
        service.register(command("gildong", "same@example.com"));

        assertThat(emailAccountRepo.findByEmail(Realm.ADMIN, "same@example.com")).isEmpty();
    }

    @Test
    @DisplayName("가입하면 그 사람으로 로그인까지 된다. 인증 시각과 인증 이벤트가 남는다")
    void registerAlsoAuthenticates() {
        // 가입 화면은 곧장 앱으로 돌아간다. 가입한 사람에게 로그인을 한 번 더 시키지 않는다.
        String principalId = service.register(command("gildong", "gildong@example.com")).principalId().value();

        Principal principal = principalRepo.byId.get(principalId);
        assertThat(principal.getLastAuthenticatedAt()).isNotNull();
        assertThat(authenticatedPublisher.published).hasSize(1);
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
