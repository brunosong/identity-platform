package com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 인가 코드를 발급한다. 이미 확인된 두 가지를 묶어 온다.
 *
 * <p>하나는 받아들이기로 한 인가 요청({@link AuthorizationRequest})이고, 다른 하나는 방금
 * 로그인이 성립한 사람이다. 둘 다 검증이 끝난 값이라 여기서 다시 따지지 않는다.
 *
 * <p><b>realm 은 인증된 사람의 것이다.</b> 경로에 적힌 realm 이 아니다. 두 값은 로그인이
 * 성립한 이상 같지만, 같다는 이유로 아무 쪽이나 쓰면 언젠가 어긋난 값이 코드에 적힌다.
 * 이 저장소는 토큰의 realm 도 같은 규칙으로 정한다 - 요청이 아니라 인증된 주체가 정한다.
 */
public record IssueAuthorizationCodeCommand(AuthorizationRequest request, Realm realm,
                                            PrincipalId principalId) {
}
