package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordEncoderPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalProfileRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.application.identity.event.SubjectRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SubjectRegisteredEventPublisher;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.PrincipalProfile;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 — auth 가 흐름을 소유한다. (brunosong RegisterWithPassword 참고)
 *
 * <p>흐름: 아이디 중복 확인 → 이메일로 기존 신원 조회(auth 의 EmailAccount), 없으면 auth 가 subjectId 를
 * 채번하고 {@link SubjectRegisteredEvent} 를 발행 → Principal + 이메일 계정 + 로컬 자격증명 저장.
 *
 * <p>이메일로 기존 신원을 찾는 일이 auth 안에서 끝난다. 전에는 대상 BC(customer)에 물었고, 그것이
 * auth 가 customer 를 알아야 하는 마지막 이유였다. 이메일은 로그인 식별자라 auth 소유가 맞다.
 *
 * <p>auth 는 누가 그 이벤트를 받는지 모른다. 프로필은 customer 가, 기본 역할은 auth 자신의 realm 별
 * 리스너가 만든다. 그래서 이 서비스는 realm 도 대상 BC 도 모른다.
 *
 * <p>모놀리식 단일 DB 라 한 트랜잭션으로 원자적이다. 소비 리스너를 {@code @EventListener}(동기)로 둬
 * 발행 트랜잭션 안에서 돌기 때문이다 — 프로필이 안 만들어지면 가입도 롤백된다. MSA 시 이 원자성은
 * saga 로 바뀐다. 토큰 발급은 하지 않는다(가입 후 로그인은 별도).
 */
@Service
public class RegisterWithPasswordService implements RegisterWithPasswordUseCase {

    private final PrincipalRepository principalRepository;
    private final PrincipalProfileRepository principalProfileRepository;
    private final PasswordAccountRepository passwordAccountRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final SubjectRegisteredEventPublisher subjectRegisteredEventPublisher;

    public RegisterWithPasswordService(PrincipalRepository principalRepository,
                                       PrincipalProfileRepository principalProfileRepository,
                                       PasswordAccountRepository passwordAccountRepository,
                                       EmailAccountRepository emailAccountRepository,
                                       PasswordEncoderPort passwordEncoder,
                                       SubjectRegisteredEventPublisher subjectRegisteredEventPublisher) {
        this.principalRepository = principalRepository;
        this.principalProfileRepository = principalProfileRepository;
        this.passwordAccountRepository = passwordAccountRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.subjectRegisteredEventPublisher = subjectRegisteredEventPublisher;
    }

    @Override
    @Transactional
    public String register(RegisterWithPasswordCommand command) {
        // 아이디 중복은 같은 realm 안에서만 따진다 — 직원 "hong" 과 고객 "hong" 은 다른 계정이다.
        if (passwordAccountRepository.existsByLoginId(command.realm(), command.loginId())) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다: " + command.loginId());
        }

        // 같은 이메일로 이미 신원이 있으면 수단(아이디/비번)만 더한다. 없으면 auth 가 채번해서
        // "생겼다"만 알린다 — 누가 받아 프로필을 만드는지는 모른다.
        Principal principal = emailAccountRepository.findByEmail(command.realm(), command.email())
                .flatMap(account -> principalRepository.findById(account.getPrincipalId()))
                .orElseGet(() -> createPrincipal(command));

        passwordAccountRepository.save(PasswordAccount.create(
                principal.getPrincipalId(), command.realm(), command.loginId(),
                passwordEncoder.encode(command.password())));

        // 인가 역할(authz)은 등록 이벤트를 받은 realm 별 리스너가 authz_subject_role 에 부여한다.
        // 신원(Principal)은 역할을 소유하지 않는다.
        return principal.getPrincipalId().value();
    }

    /** 신규 신원: subjectId 채번 → 등록 알림 → Principal 과 이메일 계정 저장. */
    private Principal createPrincipal(RegisterWithPasswordCommand command) {
        SubjectId subjectId = SubjectId.generate();
        subjectRegisteredEventPublisher.publish(new SubjectRegisteredEvent(
                subjectId.value(), command.realm(),
                command.email(), command.name(), command.phoneNumber()));

        Principal principal = principalRepository.save(Principal.create(subjectId, command.realm()));
        // 가입 폼이 받은 이름·전화번호가 갈 곳. 전에는 이벤트에 실려 나가기만 하고 아무도
        // 저장하지 않아 증발했다.
        principalProfileRepository.save(PrincipalProfile.create(
                principal.getPrincipalId(), command.name(), command.phoneNumber()));
        // 폼에 적혔을 뿐 확인한 적이 없다 — 남의 주소일 수 있다. 이 주소로 OTP 로그인을 해내면
        // 그때 확인됨으로 올라간다(AuthenticateWithEmailOtpService).
        emailAccountRepository.save(EmailAccount.unverified(
                principal.getPrincipalId(), command.realm(), command.email()));
        return principal;
    }
}
