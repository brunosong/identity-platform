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
 *
 * <p><b>네 단계가 둘로 갈린다.</b> 앞의 셋은 "이 사람이 로그인했다" 를 확정하는 일이고, 마지막
 * 하나는 "그 사람 앞으로 토큰을 내준다" 는 일이다. 자격증명을 받은 자리에서 토큰까지 내주는
 * 경로(ROPC)에서는 둘이 붙어 있어 구분할 일이 없었는데, code 흐름은 그 사이에 인가 코드와
 * 다른 요청 하나가 끼어든다. 갈라 두되 기존 경로가 밟는 순서는 그대로다.
 */
@Component
@RequiredArgsConstructor
class AuthenticationCompletion {

    private final PrincipalRepository principalRepository;
    private final PrincipalAuthenticatedEventPublisher authenticatedEventPublisher;
    private final TokenIssuance tokenIssuance;

    /**
     * 인증 성공을 확정한다. 토큰은 내주지 않는다.
     *
     * <p>소비 측(최종접속 갱신 등)은 이벤트로 처리한다. 발행만 하고 누가 받는지는 알지 않는다.
     *
     * <p>토큰이 나가지 않아도 이 사건은 남는다. 사람이 우리 앞에서 로그인한 것과 앱이 토큰을
     * 받아가는 것은 다른 일이고, 인증 시각이 가리켜야 하는 것은 앞쪽이다.
     */
    Principal establish(Principal principal) {
        principal.markAuthenticated();
        principalRepository.save(principal);

        authenticatedEventPublisher.publish(new PrincipalAuthenticatedEvent(
                principal.getSubjectId().value(), principal.getRealm(), principal.getLastAuthenticatedAt()));

        return principal;
    }

    /**
     * 인증 성공을 확정하고 <b>이어서</b> 토큰을 발급한다. 자격증명을 받은 자리에서 토큰까지
     * 내주는 경로가 쓴다.
     *
     * <p>발급할 realm 은 Principal 자신이 들고 있다. 로그인 경로가 realm 을
     * 따로 넘기면 인증된 주체와 다른 realm 의 토큰이 나갈 여지가 생긴다 — 정확히 그 어긋남을 막는 중이다.
     */
    AuthenticationResult complete(Principal principal) {
        Principal established = establish(principal);
        return tokenIssuance.resultFor(established.getRealm(), established);
    }
}
