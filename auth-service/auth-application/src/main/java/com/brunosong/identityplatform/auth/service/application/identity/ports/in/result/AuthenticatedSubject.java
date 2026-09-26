package com.brunosong.identityplatform.auth.service.application.identity.ports.in.result;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 인증이 성립했다는 사실만. <b>토큰이 없다.</b>
 *
 * <p>code 흐름에서는 사람이 로그인 화면에 비밀번호를 적는 시점과, 앱이 토큰을 받아가는 시점이
 * 다르다. 그 사이에 오가는 것은 인가 코드 한 장이고, 그때 우리가 쥐고 있어야 하는 것은
 * "누가 로그인했는가" 뿐이다. 토큰은 코드를 바꾸러 올 때 그 사람 앞으로 발급한다.
 *
 * <p>{@code PrincipalId} 를 담는다. 인가 코드에 적어 둘 값이고, 나중에 그 코드로 토큰을 만들 때
 * 다시 신원을 찾는 열쇠가 된다.
 */
public record AuthenticatedSubject(PrincipalId principalId, String subjectId, Realm realm) {
}
