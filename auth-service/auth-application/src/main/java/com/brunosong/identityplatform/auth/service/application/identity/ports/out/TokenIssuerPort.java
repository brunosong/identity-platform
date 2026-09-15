package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.RefreshedSubject;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 토큰 발급 드리븐 포트. 형식(JWT/서명/클레임)은 어댑터가 정하고, auth 는 "이 주체에게 이 realm 의
 * 토큰을 달라" 고만 말한다.
 */
public interface TokenIssuerPort {

    /** 토큰이 향할 시스템({@code aud})은 realm 이 정한다({@code RealmSystems}). */
    TokenPair issue(Realm realm, Principal principal);

    /** 검증까지 포함한다. 무효/만료/다른 realm 이면 실패로 끝난다. */
    RefreshedSubject readRefreshToken(Realm realm, String refreshToken);
}
