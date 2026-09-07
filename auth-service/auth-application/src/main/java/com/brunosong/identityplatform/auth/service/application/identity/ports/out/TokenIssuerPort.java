package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 토큰 발급 드리븐 포트. 토큰 형식(JWT 등)과 표시정보 조회는 발급 어댑터가 정한다.
 *
 * <p>realm 을 함께 받는다. 한 프로세스가 두 realm 을 모두 발급하므로 어느 realm 의 키로 서명하고
 * 어느 realm 의 권한을 실을지를 발급기가 스스로 알 수 없다. 전에는 발급기가 realm 하나에 고정돼 있었다.
 */
public interface TokenIssuerPort {

    TokenPair issue(Realm realm, Principal principal);

    /**
     * refresh 토큰을 검증(서명·만료·realm, 단일 세션 활성 시 sid 현재성)하고 담긴 subjectId 를 돌려준다.
     * 무효/만료/세션 불일치면 {@link com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException}.
     * 검증은 요청한 realm 의 공개키로만 한다 — 상대 realm 토큰은 서명 단계에서 걸린다.
     */
    String subjectIdFromRefreshToken(Realm realm, String refreshToken);
}
