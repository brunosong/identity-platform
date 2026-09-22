package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.domain.identity.LoginSession;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 로그인 세션을 시작한다. 로그인 화면에서 자격증명이 확인된 직후다.
 *
 * <p>세션 값을 그대로 돌려준다. 부르는 쪽(웹)이 그것을 쿠키에 심어야 하고, 쿠키에 적을 수명도
 * 세션이 들고 있다. 쿠키를 여기서 만들지 않는 이유는 쿠키가 HTTP 의 물건이기 때문이다 -
 * 이 계층은 그것이 무엇인지 몰라야 한다.
 *
 * <p>인증 자체는 이 유스케이스의 일이 아니다. 여기까지 온 것은 이미 확인이 끝났다는 뜻이다.
 */
public interface StartLoginSessionUseCase {

    LoginSession start(Realm realm, PrincipalId principalId);
}
