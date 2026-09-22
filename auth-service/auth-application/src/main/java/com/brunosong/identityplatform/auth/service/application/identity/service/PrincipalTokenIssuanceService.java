package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.IssueTokensForPrincipalUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신원 앞으로 토큰을 발급한다. 인증은 이미 끝나 있다.
 *
 * <p>{@code TokenIssuance} 가 이 패키지 밖으로 나가지 않게 두려고 만든 이음새다. 토큰을 만드는
 * 일은 identity 의 것이고, 인가 코드를 다루는 oauth 쪽은 "이 사람 앞으로 발급해 달라" 고만 한다.
 *
 * <p>인증 시각을 다시 남기지 않는다. 사람이 로그인한 시점은 1분 전 로그인 화면이었고, 앱이
 * 코드를 바꾸러 온 지금이 아니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrincipalTokenIssuanceService implements IssueTokensForPrincipalUseCase {

    private final PrincipalRepository principalRepository;
    private final TokenIssuance tokenIssuance;

    @Override
    public AuthenticationResult forPrincipal(PrincipalId principalId) {
        Principal principal = principalRepository.findById(principalId)
                .orElseThrow(() -> new AuthenticationFailedException("인증에 실패했습니다."));

        return tokenIssuance.resultFor(principal.getRealm(), principal);
    }
}
