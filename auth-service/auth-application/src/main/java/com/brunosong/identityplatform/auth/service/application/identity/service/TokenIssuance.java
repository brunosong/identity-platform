package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 토큰 발급 결과를 만드는 곳. 발급 자체는 {@link TokenIssuerPort} 가 한다.
 *
 * <p>발급기는 이제 필수 의존이다. 전에는 호스트가 프로퍼티로 켜는 선택 빈이라 "지연 조회 → null 이면
 * 설정 오류로 실패" 라는 절차가 필요했고, 그것이 로그인 방식마다 복사돼 있었다. 독립 서비스에서는
 * 토큰 발급이 본래 기능이라 없을 수가 없다 — 없으면 부팅이 실패하는 편이 맞다.
 */
@Component
@RequiredArgsConstructor
class TokenIssuance {

    private final TokenIssuerPort tokenIssuer;

    /** 인증된 주체에게 그 realm 의 토큰을 발급해 인증 결과로 만든다. */
    AuthenticationResult resultFor(Realm realm, Principal principal) {
        return new AuthenticationResult(
                principal.getPrincipalId().value(),
                principal.getSubjectId().value(),
                principal.getSubjectType(),
                tokenIssuer.issue(realm, principal));
    }

    /** refresh 토큰에서 주체 식별자를 읽는다. 검증은 키를 쥔 발급기가 한다. */
    String subjectIdFromRefreshToken(Realm realm, String refreshToken) {
        return tokenIssuer.subjectIdFromRefreshToken(realm, refreshToken);
    }
}
