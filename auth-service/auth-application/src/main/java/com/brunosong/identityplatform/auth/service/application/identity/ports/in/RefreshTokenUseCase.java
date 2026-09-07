package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;

/**
 * 리프레시 토큰으로 액세스/리프레시 토큰을 재발급하는 인바운드 포트.
 *
 * <p>refresh 토큰을 검증하고 담긴 주체로 Principal 을 다시 로딩해 새 토큰을 발급한다. 재발급 시점의 권한
 * (authLs)과 realm 리비전(rbacRev)이 다시 반영되므로, 로그인 유지 중에도 정책 변경이 갱신 때 흡수된다.
 * 무효/만료/세션 불일치면 {@link com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException}.
 *
 * <p>주체 유형을 함께 받는다. refresh 토큰에는 subjectId 만 실려 있고 그것만으로는 주체가 특정되지 않는다
 * (유일키는 유형+식별자다). 로그인과 같은 출처에서 유형을 받아, 재발급도 같은 realm 안에서만 성립하게 한다.
 */
public interface RefreshTokenUseCase {

    AuthenticationResult refresh(SubjectType subjectType, String refreshToken);
}
