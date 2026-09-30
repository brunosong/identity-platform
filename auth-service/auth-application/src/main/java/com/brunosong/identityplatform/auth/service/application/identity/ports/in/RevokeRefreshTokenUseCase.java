package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * refresh 토큰의 계보를 끊는다. 로그아웃이 부른다.
 *
 * <p>로그인 세션을 끊어도 앱이 든 refresh 토큰은 수명 동안 살아서 새 access 를 받아 간다. 로그아웃이
 * 그 길까지 막으려면 계보를 끊어야 한다. 끊긴 계보의 토큰은 어느 것이든 재발급에서 거절된다.
 *
 * <p>틀린 토큰이어도 실패로 알리지 않는다. 로그아웃은 끝내는 것이 목적이고, 이미 끝났거나 처음부터
 * 없던 것은 끝난 것과 같다(RFC 7009 2.2).
 */
public interface RevokeRefreshTokenUseCase {

    void revoke(Realm realm, String refreshToken);
}
