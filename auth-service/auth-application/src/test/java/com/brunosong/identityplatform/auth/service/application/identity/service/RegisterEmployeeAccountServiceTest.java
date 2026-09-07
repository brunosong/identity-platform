package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterEmployeeAccountCommand;
import com.brunosong.identityplatform.auth.service.application.identity.event.EmployeeRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmployeeRegisteredEventPublisher;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeEmailAccountRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 직원 계정 등록 — auth 가 esntlId 를 채번하고, 같은 값으로 등록을 알리는지 본다.
 *
 * <p>프로필을 실제로 만드는 것은 이벤트를 받는 employee 리스너라 여기서는 발행까지만 본다.
 */
class RegisterEmployeeAccountServiceTest {

    private FakePrincipalRepository principalRepo;
    private FakeEmailAccountRepository emailAccountRepo;
    private RecordingEmployeeRegisteredPublisher registeredPublisher;
    private RegisterEmployeeAccountService service;

    @BeforeEach
    void setUp() {
        principalRepo = new FakePrincipalRepository();
        emailAccountRepo = new FakeEmailAccountRepository();
        registeredPublisher = new RecordingEmployeeRegisteredPublisher();
        service = new RegisterEmployeeAccountService(principalRepo, emailAccountRepo, registeredPublisher);
    }

    private RegisterEmployeeAccountCommand command(String email) {
        return new RegisterEmployeeAccountCommand(
                "EMP001", "홍길동", email, "01012345678", "대리", "ORG01", "P");
    }

    @Test
    @DisplayName("등록하면 채번한 esntlId 로 Principal 을 만들고 같은 값으로 등록을 알린다")
    void registerSharesEssentialId() {
        String essentialId = service.register(command("gildong@example.com"));

        Principal principal = principalRepo.findBySubjectId(SubjectType.EMPLOYEE, new SubjectId(essentialId)).orElseThrow();
        assertThat(principal.getSubjectType()).isEqualTo(SubjectType.EMPLOYEE);
        assertThat(registeredPublisher.published).hasSize(1);
        assertThat(registeredPublisher.published.get(0).essentialId()).isEqualTo(essentialId);
    }

    @Test
    @DisplayName("프로필을 만드는 데 필요한 값이 이벤트에 실린다")
    void eventCarriesProfileFields() {
        service.register(command("gildong@example.com"));

        EmployeeRegisteredEvent event = registeredPublisher.published.get(0);
        assertThat(event.employeeId()).isEqualTo("EMP001");
        assertThat(event.name()).isEqualTo("홍길동");
        assertThat(event.email()).isEqualTo("gildong@example.com");
        assertThat(event.organizationId()).isEqualTo("ORG01");
        assertThat(event.statusCode()).isEqualTo("P");
    }

    @Test
    @DisplayName("이메일이 있으면 OTP 로그인용 이메일 계정이 함께 생긴다")
    void emailAccountIsCreated() {
        service.register(command("gildong@example.com"));

        assertThat(emailAccountRepo.findByEmail(SubjectType.EMPLOYEE, "gildong@example.com")).isPresent();
    }

    @Test
    @DisplayName("이메일이 없으면 이메일 계정은 만들지 않는다")
    void withoutEmailNoEmailAccount() {
        service.register(command(null));

        assertThat(emailAccountRepo.byEmail).isEmpty();
    }

    private static final class RecordingEmployeeRegisteredPublisher implements EmployeeRegisteredEventPublisher {
        final List<EmployeeRegisteredEvent> published = new ArrayList<>();

        @Override
        public void publish(EmployeeRegisteredEvent event) {
            published.add(event);
        }
    }
}
