package com.brunosong.identityplatform.auth.service.application.identity.ports.in.result;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;

/**
 * 인증 성공 결과: 신원(principalId/subjectId) + 발급 토큰. 호스트 컨트롤러가 쿠키 세팅에 사용.
 * 역할/권한은 토큰(authLs 클레임)에 실려 있으므로 여기 담지 않는다.
 */
public record AuthenticationResult(
        String principalId,
        String subjectId,
        Realm realm,
        TokenPair tokens
) {
}
