package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 토큰 발급기 접근을 한곳으로 모은다.
 *
 * <p>발급기는 호스트가 프로퍼티로 켜는 선택 빈이라 없을 수 있다. 그래서 "지연 조회 → null 이면
 * 설정 오류로 실패" 라는 짧은 절차가 필요한데, 그것이 로그인 방식마다 복사돼 있었다(비밀번호·OTP·소셜·재발급).
 * 발급 결과를 만드는 코드도 함께 복사돼 있었다 — 한 군데서만 하도록 모은다.
 */
@Component
@RequiredArgsConstructor
class TokenIssuance {

    private final ObjectProvider<TokenIssuerPort> tokenIssuerProvider;

    /** 인증된 주체에게 토큰을 발급해 인증 결과로 만든다. */
    AuthenticationResult resultFor(Principal principal) {
        return new AuthenticationResult(
                principal.getPrincipalId().value(),
                principal.getSubjectId().value(),
                principal.getSubjectType(),
                issuer().issue(principal));
    }

    /** refresh 토큰에서 주체 식별자를 읽는다. 검증은 키를 쥔 발급기가 한다. */
    String subjectIdFromRefreshToken(String refreshToken) {
        return issuer().subjectIdFromRefreshToken(refreshToken);
    }

    private TokenIssuerPort issuer() {
        TokenIssuerPort issuer = tokenIssuerProvider.getIfAvailable();
        if (issuer == null) {
            throw new IllegalStateException("이 호스트에는 TokenIssuerPort 가 없습니다(토큰 발급 미지원).");
        }
        return issuer;
    }
}
