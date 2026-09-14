package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.event.SubjectRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithEmailUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithEmailCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalProfileRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SubjectRegisteredEventPublisher;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.PrincipalProfile;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 없는 셀프 가입 — 이메일 인증번호로 주소를 확인하고 신원을 만든다.
 *
 * <p>흐름: 인증번호 검증 → 이미 그 주소의 신원이 있으면 그대로 돌려줌 → 없으면 subjectId 채번 후
 * {@link SubjectRegisteredEvent} 발행 → Principal + 이메일 계정 저장.
 *
 * <h2>비밀번호 가입과 무엇이 다른가</h2>
 * {@code RegisterWithPasswordService} 는 <b>누구나</b> 남의 주소를 적어 가입할 수 있다 — 그 주소의
 * 주인인지 확인하지 않기 때문이다. 여기서는 인증번호를 받아낸 것이 곧 그 주소의 주인이라는
 * 증거라서, <b>가입 시점에 이메일 소유가 확인된다.</b>
 *
 * <p>대신 만드는 것이 적다. 비밀번호 계정을 만들지 않으므로 이 사람은 이메일 OTP 로만 들어온다.
 * 직원(어드민 realm)에게는 그것이 유일한 방식이고, 고객(포털)에게는 두 방식 중 하나다.
 *
 * <h2>역할은 여기서 주지 않는다</h2>
 * 비밀번호 가입과 똑같이 {@link SubjectRegisteredEvent} 만 발행하고, 역할 부여는 그 이벤트를 받는
 * realm 별 리스너가 한다. 그래서 포털로 가입하면 {@code CUSTOMER} 역할이 붙고, 어드민으로
 * 가입하면 <b>아무 역할도 붙지 않는다</b> — 그 리스너가 고객만 상대하기 때문이다.
 * 어드민 셀프 가입을 열어도 안전한 이유가 이것이다: 신원은 스스로 만들 수 있지만 권한은
 * 운영자만 준다.
 *
 * <p>토큰은 발급하지 않는다. 가입과 로그인은 별개의 요청이라는 규칙을 두 가입 방식이 똑같이 따른다.
 */
@Service
@RequiredArgsConstructor
public class RegisterWithEmailService implements RegisterWithEmailUseCase {

    private final PrincipalRepository principalRepository;
    private final PrincipalProfileRepository principalProfileRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailOtpVerifier otpVerifier;
    private final SubjectRegisteredEventPublisher subjectRegisteredEventPublisher;

    @Override
    @Transactional
    public String register(RegisterWithEmailCommand command) {
        // 먼저 코드를 확인한다. 통과하지 못하면 아래로 내려가지 않으므로, 이 주소가 이미 가입돼
        // 있는지 여부가 응답에 드러나지 않는다 — 가입용 코드는 미등록 주소로만 나가기 때문에
        // 등록된 주소로는 애초에 유효한 코드가 존재하지 않는다.
        otpVerifier.verify(command.email(), command.verificationCode());

        // 여기 도달했는데 이미 신원이 있다면, 코드를 받은 뒤 사이에 만들어졌거나 로그인용 코드를
        // 들고 온 경우다. 새로 만들면 이메일 유일 제약에 걸리므로 있는 것을 그대로 돌려준다 —
        // 그 주소의 주인임은 방금 증명했으니 새로 알려주는 것도 없다.
        return emailAccountRepository.findByEmail(command.realm(), command.email())
                .flatMap(account -> principalRepository.findById(account.getPrincipalId()))
                .orElseGet(() -> createPrincipal(command))
                .getPrincipalId().value();
    }

    /** 신규 신원: subjectId 채번 → 등록 알림 → Principal 과 이메일 계정 저장. */
    private Principal createPrincipal(RegisterWithEmailCommand command) {
        SubjectId subjectId = SubjectId.generate();
        subjectRegisteredEventPublisher.publish(new SubjectRegisteredEvent(
                subjectId.value(), command.realm(),
                command.email(), command.name(), command.phoneNumber()));

        Principal principal = principalRepository.save(Principal.create(subjectId, command.realm()));
        principalProfileRepository.save(PrincipalProfile.create(
                principal.getPrincipalId(), command.name(), command.phoneNumber()));
        // 인증번호를 받아냈으므로 이 주소의 주인이 맞다.
        emailAccountRepository.save(EmailAccount.verified(
                principal.getPrincipalId(), command.realm(), command.email()));
        return principal;
    }
}
