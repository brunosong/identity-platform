package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;

/**
 * 토큰 발급 드리븐 포트. 토큰 형식(JWT 등)과 표시정보 조회는 발급 어댑터가 정한다.
 * Principal(subjectId+roles)만 받는다 — 이름/이메일 등 주체 프로필은 어댑터가 subjectId 로 조회한다.
 */
public interface TokenIssuerPort {

    TokenPair issue(Principal principal);

    /**
     * refresh 토큰을 검증(서명·만료, 단일 세션 활성 시 sid 현재성)하고 담긴 subjectId 를 돌려준다.
     * 무효/만료/세션 불일치면 {@link com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException}.
     * 키를 쥔 발급기가 자기 토큰을 검증한다(재발급은 이 subjectId 로 Principal 을 다시 로딩해 issue).
     */
    String subjectIdFromRefreshToken(String refreshToken);
}
