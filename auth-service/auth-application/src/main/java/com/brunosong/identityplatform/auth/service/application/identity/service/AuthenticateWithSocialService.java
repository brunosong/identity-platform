package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.EstablishSocialAuthenticationUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalProfileRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialIdentityVerifierPort;
import com.brunosong.identityplatform.auth.service.application.identity.event.SubjectRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SubjectRegisteredEventPublisher;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.VerifiedSocialIdentity;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.PrincipalProfile;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 소셜 로그인. provider 검증은 아웃바운드 어댑터({@link SocialIdentityVerifierPort})가, 링크/발급은 auth 가 한다.
 *
 * <p>이미 연결된 소셜이면 그 Principal 로 발급하고, 처음이면 verified email 로 주체를 resolve 해(없으면
 * 프로비저닝) 같은 Principal 에 SocialAccount 를 연결한다. 그래서 비밀번호로 가입했던 고객이 소셜로 처음
 * 들어와도 새 Principal 이 생기지 않고 기존 Principal 에 수단만 추가된다(계정 자동 연결, verified email 한정).
 */
@Service
public class AuthenticateWithSocialService implements EstablishSocialAuthenticationUseCase {

    /**
     * provider 검증 어댑터. 아직 이 서비스에 구현이 없어 선택 조회로 둔다 — 어댑터가 들어오면
     * 필수 의존으로 바꾼다(그때 이 지연 조회와 아래 예외가 함께 사라진다).
     */
    private final ObjectProvider<SocialIdentityVerifierPort> socialVerifierProvider;
    private final SocialAccountRepository socialAccountRepository;
    private final PrincipalRepository principalRepository;
    private final PrincipalProfileRepository principalProfileRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final SubjectRegisteredEventPublisher subjectRegisteredEventPublisher;
    private final AuthenticationCompletion authenticationCompletion;

    public AuthenticateWithSocialService(ObjectProvider<SocialIdentityVerifierPort> socialVerifierProvider,
                                         SocialAccountRepository socialAccountRepository,
                                         PrincipalRepository principalRepository,
                                         PrincipalProfileRepository principalProfileRepository,
                                         EmailAccountRepository emailAccountRepository,
                                         SubjectRegisteredEventPublisher subjectRegisteredEventPublisher,
                                         AuthenticationCompletion authenticationCompletion) {
        this.socialVerifierProvider = socialVerifierProvider;
        this.socialAccountRepository = socialAccountRepository;
        this.principalRepository = principalRepository;
        this.principalProfileRepository = principalProfileRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.subjectRegisteredEventPublisher = subjectRegisteredEventPublisher;
        this.authenticationCompletion = authenticationCompletion;
    }

    /** 인증까지만 한다. 토큰은 code 교환에서 나간다. */
    @Override
    @Transactional
    public AuthenticatedSubject withSocial(SocialAuthCommand command) {
        Principal principal = authenticationCompletion.establish(resolve(command));
        return new AuthenticatedSubject(principal.getPrincipalId(),
                principal.getSubjectId().value(), principal.getRealm());
    }

    /** provider 에 신원을 묻고, 그 신원에 해당하는 우리 Principal 을 찾거나 만든다. */
    private Principal resolve(SocialAuthCommand command) {
        SocialIdentityVerifierPort verifier = socialVerifierProvider.getIfAvailable();
        if (verifier == null) {
            throw new IllegalStateException("소셜 검증 어댑터가 설정되지 않았습니다(SocialIdentityVerifierPort).");
        }
        VerifiedSocialIdentity id = verifier.verify(command.provider(), command.authorizationCode());

        // 이미 연결된 소셜이면 그 Principal, 아니면 verified email 로 주체 resolve 후 링크/생성
        return socialAccountRepository
                .findByProvider(command.realm(), command.provider(), id.providerUid())
                .map(sa -> principalRepository.findById(sa.getPrincipalId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Principal not found for socialAccount=" + sa.getSocialAccountId())))
                .orElseGet(() -> linkOrCreate(command.realm(), command.provider(), id));
    }

    /**
     * 최초 소셜 로그인: <b>확인된</b> 이메일로 같은 주체를 찾으면 그 Principal 에 SocialAccount 만
     * 추가한다(자동 연결). 없으면 새 신원을 만든다.
     *
     * <h2>자동 연결의 키는 "확인된" 이메일이어야 한다</h2>
     * 양쪽 모두 확인돼야 한다.
     *
     * <ul>
     *   <li><b>provider 쪽</b> — provider 가 소유를 검증한 주소여야 한다. 안 주면 거절한다.</li>
     *   <li><b>우리 쪽</b> — 기존 이메일 계정도 확인된 것이어야 한다. 비밀번호 가입 폼에 적힌
     *       주소는 남의 것일 수 있다.</li>
     * </ul>
     *
     * <p>우리 쪽 확인을 빠뜨리면 <b>pre-account hijacking</b> 이 된다 — 공격자가 victim@x 로
     * 비밀번호 가입을 해두고 기다리면, victim 이 소셜로 처음 들어올 때 공격자의 신원에 붙는다.
     * 둘이 한 계정을 쓰게 되고 공격자는 자기 비밀번호로 계속 들어온다.
     *
     * <p>그 경우 <b>조용히 새 신원을 만들지 않고 거절한다.</b> 새로 만들면 이메일 유일 제약에 걸려
     * 어차피 실패하는데, 그 실패는 500 으로 나가 원인이 보이지 않는다. 여기서 막으면 무엇을 해야
     * 하는지 말해 줄 수 있다 — 기존 수단으로 로그인한 뒤 소셜을 연결하는 것이다.
     *
     * <p>이것이 피해자를 소셜 로그인에서 막아 버리는 것은 사실이다. 하지만 남의 계정에 붙는 것보다
     * 낫고, 진짜 해법은 <b>비밀번호 가입에서도 이메일을 확인하는 것</b>이다 — 그러면 공격자가
     * 남의 주소로 미확인 계정을 심어둘 수 없다.
     */
    private Principal linkOrCreate(Realm realm, SocialProvider provider, VerifiedSocialIdentity id) {
        if (id.email() == null || id.email().isBlank()) {
            // provider 가 verified email 을 주지 않은 경우 자동 연결 금지(탈취 방지) — 명시적 연결 플로우로만.
            throw new AuthenticationFailedException("소셜 계정에서 확인된 이메일을 받지 못했습니다.");
        }

        Optional<EmailAccount> existing = emailAccountRepository.findByEmail(realm, id.email());
        if (existing.isPresent() && !existing.get().isVerified()) {
            throw new AuthenticationFailedException(
                    "이 이메일은 다른 방법으로 가입돼 있습니다. 기존 방법으로 로그인한 뒤 소셜 계정을 연결해주세요.");
        }

        Principal principal = existing
                .flatMap(account -> principalRepository.findById(account.getPrincipalId()))
                .orElseGet(() -> {
                    SubjectId subjectId = SubjectId.generate();
                    subjectRegisteredEventPublisher.publish(new SubjectRegisteredEvent(
                            subjectId.value(), realm, id.email(), id.name(), null));
                    Principal created = principalRepository.save(Principal.create(subjectId, realm));
                    principalProfileRepository.save(PrincipalProfile.create(
                            created.getPrincipalId(), displayName(id), null));
                    // provider 가 소유를 검증한 주소만 여기까지 온다(위 guard).
                    emailAccountRepository.save(EmailAccount.verified(
                            created.getPrincipalId(), realm, id.email()));
                    return created;
                });

        socialAccountRepository.save(SocialAccount.create(
                principal.getPrincipalId(), realm, provider, id.providerUid()));
        return principal;
    }

    /**
     * 화면에 보일 이름. provider 가 주면 그것을 쓰고, 안 주면 이메일 아이디 부분으로 대신한다.
     *
     * <p>지어내는 값이지만 비워둘 수가 없다 — 프로필의 이름은 비어 있으면 안 되고(관리 콘솔이
     * UUID 만 보게 된다), provider 가 이름을 주지 않는 경우가 실제로 있다. 본인이 나중에 고치는
     * 것을 전제로 <b>자리만 채운다.</b> 가입 폼이 있는 경로(비밀번호/이메일)는 이름을 필수로 받으므로
     * 이 폴백을 타지 않는다.
     */
    private static String displayName(VerifiedSocialIdentity id) {
        if (id.name() != null && !id.name().isBlank()) {
            return id.name();
        }
        return id.email().substring(0, id.email().indexOf('@'));
    }
}
