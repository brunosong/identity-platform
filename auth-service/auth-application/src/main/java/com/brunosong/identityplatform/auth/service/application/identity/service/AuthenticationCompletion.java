package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.event.PrincipalAuthenticatedEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalAuthenticatedEventPublisher;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 인증이 성립한 뒤에 늘 같은 순서로 해야 하는 일 — 인증 시각 기록, 저장, 인증 이벤트 발행.
 *
 * <p>비밀번호·OTP·소셜 세 경로가 각자 이 단계를 복사해 갖고 있었다. 단계가 하나 늘거나 순서가 바뀌면
 * 세 곳을 같이 고쳐야 하고, 실제로 한 곳만 고쳐지면 그 로그인 방식만 조용히 다르게 동작한다.
 *
 * <p>토큰은 여기서 내주지 않는다. 사람이 로그인하는 것과 앱이 토큰을 받아가는 것 사이에 인가
 * 코드와 다른 요청 하나가 끼어든다. 토큰은 그 요청(code 교환)에서 나간다.
 */
@Component
@RequiredArgsConstructor
class AuthenticationCompletion {

    private final PrincipalRepository principalRepository;
    private final PrincipalAuthenticatedEventPublisher authenticatedEventPublisher;

    /**
     * 인증 성공을 확정한다.
     *
     * <p>소비 측(최종접속 갱신 등)은 이벤트로 처리한다. 발행만 하고 누가 받는지는 알지 않는다.
     *
     * <p>인증 시각이 가리키는 것은 사람이 우리 앞에서 로그인한 때다. 앱이 토큰을 받아가는 때가 아니다.
     */
    Principal establish(Principal principal) {
        principal.markAuthenticated();
        principalRepository.save(principal);

        authenticatedEventPublisher.publish(new PrincipalAuthenticatedEvent(
                principal.getSubjectId().value(), principal.getRealm(), principal.getLastAuthenticatedAt()));

        return principal;
    }
}
