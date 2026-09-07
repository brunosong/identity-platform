package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterEmployeeAccountUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterEmployeeAccountCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.event.EmployeeRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmployeeRegisteredEventPublisher;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 직원 계정 등록 — auth 가 신원 권위자로서 흐름을 소유한다.
 *
 * <p>순서: (1) esntlId 채번 → (2) Principal 생성(EMPLOYEE, esntlId, email) → (3) 같은 esntlId 로
 * {@link EmployeeRegisteredEvent} 발행. auth 는 누가 그것을 받아 프로필을 만드는지 모른다.
 * 소비 리스너가 {@code @EventListener}(동기)라 한 트랜잭션으로 원자적이다(프로필 실패 시 Principal 도 롤백).
 *
 * <p>토큰 발급은 하지 않는다(등록 후 로그인은 별도 OTP). 인가(RBAC) 역할 배정도 별도(admin).
 *
 * <p>직원 전용 유스케이스다. 전에는 realm 프로퍼티로 이 빈 자체를 껐지만, 한 서비스가 두 realm 을
 * 담당하면 그렇게 가를 수 없다. 대신 이 서비스가 만드는 것이 언제나 EMPLOYEE 신원이라는 사실
 * (아래 {@code SubjectType.EMPLOYEE})과, 이것을 부르는 경로가 직원 등록 API 하나뿐이라는 점이 경계다.
 */
@Service
@RequiredArgsConstructor
public class RegisterEmployeeAccountService implements RegisterEmployeeAccountUseCase {

    private final PrincipalRepository principalRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmployeeRegisteredEventPublisher employeeRegisteredEventPublisher;

    @Override
    @Transactional
    public String register(RegisterEmployeeAccountCommand command) {
        String essentialId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        // (1)(2) 신원 먼저: Principal 생성(순수 신원 — 식별자 email 은 EmailAccount 소유). 인가 역할(authz)은
        // 신원이 아니라 admin RBAC 이 authz_subject_role 에 별도 배정한다.
        Principal principal = Principal.create(new SubjectId(essentialId), SubjectType.EMPLOYEE);
        principalRepository.save(principal);

        // 이메일(OTP) 로그인 식별자 계정 생성
        if (command.email() != null && !command.email().isBlank()) {
            emailAccountRepository.save(EmailAccount.create(
                    principal.getPrincipalId(), SubjectType.EMPLOYEE, command.email()));
        }

        // (3) 같은 esntlId 로 "직원이 생겼다"만 알린다. 프로필만 싣는다 — 자격증명은 auth 가 갖는다.
        employeeRegisteredEventPublisher.publish(new EmployeeRegisteredEvent(
                essentialId, command.employeeId(), command.name(), command.email(),
                command.mobile(), command.positionName(), command.organizationId(), command.statusCode()));

        return essentialId;
    }
}
