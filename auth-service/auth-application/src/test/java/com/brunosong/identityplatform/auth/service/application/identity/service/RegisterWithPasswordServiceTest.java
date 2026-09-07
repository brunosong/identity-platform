package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.event.SubjectRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePasswordEncoder;
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
    private FakePasswordAccountRepository accountRepo;
    private FakeEmailAccountRepository emailAccountRepo;
    private RecordingSubjectRegisteredPublisher registeredPublisher;
    private RegisterWithPasswordService service;

    @BeforeEach
    void setUp() {
        principalRepo = new FakePrincipalRepository();
        accountRepo = new FakePasswordAccountRepository();
        emailAccountRepo = new FakeEmailAccountRepository();
        registeredPublisher = new RecordingSubjectRegisteredPublisher();
        service = new RegisterWithPasswordService(
                principalRepo, accountRepo, emailAccountRepo, new FakePasswordEncoder(),
                registeredPublisher);
    }

    private RegisterWithPasswordCommand command(String loginId, String email) {
        return new RegisterWithPasswordCommand(
                SubjectType.CUSTOMER, email, "홍길동", "01012345678", loginId, "pw1234!");
    }

    @Test
    @DisplayName("신규 가입은 주체 등록을 알리고 Principal 과 비밀번호 계정을 남긴다")
    void registerPublishesSubjectRegistered() {
        String principalId = service.register(command("gildong", "gildong@example.com"));

        assertThat(registeredPublisher.published).hasSize(1);
        assertThat(principalRepo.byId).containsKey(principalId);
        assertThat(accountRepo.findByLoginId(SubjectType.CUSTOMER, "gildong")).isPresent();
    }

    @Test
    @DisplayName("subjectId 는 auth 가 채번해서 이벤트에 실어 보낸다")
    void authMintsSubjectId() {
        String principalId = service.register(command("gildong", "gildong@example.com"));

        assertThat(principalRepo.byId.get(principalId).getSubjectId().value())
                .isEqualTo(registeredPublisher.published.get(0).subjectId());
    }

    @Test
    @DisplayName("프로필을 만드는 데 필요한 값이 이벤트에 실린다")
    void eventCarriesProfileFields() {
        service.register(command("gildong", "gildong@example.com"));

        SubjectRegisteredEvent event = registeredPublisher.published.get(0);
        assertThat(event.subjectType()).isEqualTo(SubjectType.CUSTOMER);
        assertThat(event.email()).isEqualTo("gildong@example.com");
        assertThat(event.name()).isEqualTo("홍길동");
        assertThat(event.phoneNumber()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("비밀번호는 원문이 아니라 인코딩되어 저장된다")
    void passwordIsEncoded() {
        service.register(command("gildong", "gildong@example.com"));

        assertThat(accountRepo.findByLoginId(SubjectType.CUSTOMER, "gildong").orElseThrow().getPasswordHash())
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
    @DisplayName("같은 이메일의 주체가 이미 있으면 Principal 을 새로 만들지 않고 수단만 더한다")
    void existingSubjectReusesPrincipal() {
        String first = service.register(command("gildong", "same@example.com"));

        String second = service.register(command("gildong2", "same@example.com"));

        assertThat(second).isEqualTo(first);
        assertThat(principalRepo.byId).hasSize(1);
        assertThat(registeredPublisher.published).hasSize(1);
        // 저장소 내부 키가 아니라 포트로 확인한다 — 아이디는 유형 안에서만 유일하다.
        assertThat(accountRepo.findByLoginId(SubjectType.CUSTOMER, "gildong")).isPresent();
        assertThat(accountRepo.findByLoginId(SubjectType.CUSTOMER, "gildong2")).isPresent();
    }

    @Test
    @DisplayName("가입하면 이메일로 신원을 되찾을 수 있게 이메일 계정을 남긴다")
    void emailAccountIsCreated() {
        String principalId = service.register(command("gildong", "gildong@example.com"));

        assertThat(emailAccountRepo.findByEmail(SubjectType.CUSTOMER, "gildong@example.com"))
                .get()
                .extracting(a -> a.getPrincipalId().value())
                .isEqualTo(principalId);
    }

    @Test
    @DisplayName("같은 이메일이라도 주체 유형이 다르면 서로 다른 신원이다")
    void sameEmailDifferentSubjectType() {
        service.register(command("gildong", "same@example.com"));

        assertThat(emailAccountRepo.findByEmail(SubjectType.EMPLOYEE, "same@example.com")).isEmpty();
    }

    @Test
    @DisplayName("가입 자체는 인증이 아니라서 Principal 의 최종 인증 시각을 남기지 않는다")
    void registerDoesNotAuthenticate() {
        String principalId = service.register(command("gildong", "gildong@example.com"));

        Principal principal = principalRepo.byId.get(principalId);
        assertThat(principal.getLastAuthenticatedAt()).isNull();
    }
}
