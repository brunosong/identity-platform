package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;

/**
 * 리프레시 토큰으로 액세스/리프레시 토큰을 재발급하는 인바운드 포트.
 *
 * <p>refresh 토큰을 검증하고 담긴 주체로 Principal 을 다시 로딩해 새 토큰을 발급한다. 재발급 시점의 권한
 * (authLs)과 realm 리비전(rbacRev)이 다시 반영되므로, 로그인 유지 중에도 정책 변경이 갱신 때 흡수된다.
 * 무효/만료/세션 불일치면 {@link com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException}.
 */
public interface RefreshTokenUseCase {

    AuthenticationResult refresh(String refreshToken);
}
