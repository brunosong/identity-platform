package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithSocialUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
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
import com.brunosong.identityplatform.auth.service.domain.identity.SocialAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인. provider 검증은 호스트 아웃바운드({@link SocialIdentityVerifierPort})가, 링크/발급은 auth 가 한다.
 *
 * <p>이미 연결된 소셜이면 그 Principal 로 발급하고, 처음이면 verified email 로 주체를 resolve 해(없으면
 * 프로비저닝) 같은 Principal 에 SocialAccount 를 연결한다. 그래서 비밀번호로 가입했던 고객이 소셜로 처음
 * 들어와도 새 Principal 이 생기지 않고 기존 Principal 에 수단만 추가된다(계정 자동 연결, verified email 한정).
 */
@Service
public class AuthenticateWithSocialService implements AuthenticateWithSocialUseCase {

    /** provider 검증기는 호스트가 제공. 없으면 소셜 로그인 미지원이라 지연 조회한다(부팅 영향 없음). */
    private final ObjectProvider<SocialIdentityVerifierPort> socialVerifierProvider;
    private final SocialAccountRepository socialAccountRepository;
    private final PrincipalRepository principalRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final SubjectRegisteredEventPublisher subjectRegisteredEventPublisher;
    private final AuthenticationCompletion authenticationCompletion;

    public AuthenticateWithSocialService(ObjectProvider<SocialIdentityVerifierPort> socialVerifierProvider,
                                         SocialAccountRepository socialAccountRepository,
                                         PrincipalRepository principalRepository,
                                         EmailAccountRepository emailAccountRepository,
                                         SubjectRegisteredEventPublisher subjectRegisteredEventPublisher,
                                         AuthenticationCompletion authenticationCompletion) {
        this.socialVerifierProvider = socialVerifierProvider;
        this.socialAccountRepository = socialAccountRepository;
        this.principalRepository = principalRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.subjectRegisteredEventPublisher = subjectRegisteredEventPublisher;
        this.authenticationCompletion = authenticationCompletion;
    }

    @Override
    @Transactional
    public AuthenticationResult authenticate(SocialAuthCommand command) {
        SocialIdentityVerifierPort verifier = socialVerifierProvider.getIfAvailable();
        if (verifier == null) {
            throw new IllegalStateException("이 호스트에는 SocialIdentityVerifierPort 가 없습니다(소셜 로그인 미지원).");
        }
        VerifiedSocialIdentity id = verifier.verify(command.provider(), command.authorizationCode());

        // 이미 연결된 소셜이면 그 Principal, 아니면 verified email 로 주체 resolve 후 링크/생성
        Principal principal = socialAccountRepository.findByProvider(command.provider(), id.providerUid())
                .map(sa -> principalRepository.findById(sa.getPrincipalId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Principal not found for socialAccount=" + sa.getSocialAccountId())))
                .orElseGet(() -> linkOrCreate(command.subjectType(), command.provider(), id));

        return authenticationCompletion.complete(principal);
    }

    /** 최초 소셜 로그인: verified email 로 같은 주체를 찾으면 그 Principal 에 SocialAccount 만 추가(자동 연결). */
    private Principal linkOrCreate(SubjectType subjectType, SocialProvider provider, VerifiedSocialIdentity id) {
        if (id.email() == null || id.email().isBlank()) {
            // provider 가 verified email 을 주지 않은 경우 자동 연결 금지(탈취 방지) — 명시적 연결 플로우로만.
            throw new AuthenticationFailedException("소셜 계정에서 확인된 이메일을 받지 못했습니다.");
        }
        // 같은 이메일로 이미 신원이 있으면 그 Principal 에 소셜 수단만 붙인다(JIT 는 없을 때만).
        Principal principal = emailAccountRepository.findByEmail(subjectType, id.email())
                .flatMap(account -> principalRepository.findById(account.getPrincipalId()))
                .orElseGet(() -> {
                    SubjectId subjectId = SubjectId.generate();
                    subjectRegisteredEventPublisher.publish(new SubjectRegisteredEvent(
                            subjectId.value(), subjectType, id.email(), id.name(), null));
                    Principal created = principalRepository.save(Principal.create(subjectId, subjectType));
                    emailAccountRepository.save(EmailAccount.create(
                            created.getPrincipalId(), subjectType, id.email()));
                    return created;
                });

        socialAccountRepository.save(SocialAccount.create(principal.getPrincipalId(), provider, id.providerUid()));
        return principal;
    }
}
