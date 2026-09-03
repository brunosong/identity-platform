package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.event.PrincipalAuthenticatedEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalAuthenticatedEventPublisher;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 인증이 성립한 뒤에 늘 같은 순서로 해야 하는 일 — 인증 시각 기록, 저장, 인증 이벤트 발행, 토큰 발급.
 *
 * <p>비밀번호·OTP·소셜 세 경로가 각자 이 네 단계를 복사해 갖고 있었다. 단계가 하나 늘거나 순서가 바뀌면
 * 세 곳을 같이 고쳐야 하고, 실제로 한 곳만 고쳐지면 그 로그인 방식만 조용히 다르게 동작한다.
 */
@Component
@RequiredArgsConstructor
class AuthenticationCompletion {

    private final PrincipalRepository principalRepository;
    private final PrincipalAuthenticatedEventPublisher authenticatedEventPublisher;
    private final TokenIssuance tokenIssuance;

    /**
     * 인증 성공을 확정하고 토큰을 발급한다.
     *
     * <p>소비 측(최종접속 갱신 등)은 이벤트로 처리한다. 발행만 하고 누가 받는지는 알지 않는다.
     */
    AuthenticationResult complete(Principal principal) {
        principal.markAuthenticated();
        principalRepository.save(principal);

        authenticatedEventPublisher.publish(new PrincipalAuthenticatedEvent(
                principal.getSubjectId().value(), principal.getSubjectType(), principal.getLastAuthenticatedAt()));

        return tokenIssuance.resultFor(principal);
    }
}
