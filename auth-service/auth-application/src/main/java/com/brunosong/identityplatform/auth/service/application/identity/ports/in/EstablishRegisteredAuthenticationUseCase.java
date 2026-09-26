package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;

/**
 * 방금 가입한 사람을 그대로 로그인시킨다. 인증 시각과 인증 이벤트를 남긴다. 토큰은 발급하지 않는다.
 *
 * <p>가입과 나눠 둔 것은 가입 뒤 로그인을 시킬지를 <b>어느 입구로 왔는가</b>가 정하기 때문이다.
 * 앱의 인가 요청(prompt=create)으로 온 가입은 곧장 앱으로 돌아가야 하니 여기를 부르고, 가입만 하는
 * 입구는 부르지 않는다. 가입 유스케이스가 로그인까지 하면 로그인하지 않은 사람에게 로그인 기록이 남는다.
 *
 * <p>가입이 방금 이메일 주인임을 증명했으므로 자격증명을 다시 묻지 않는다.
 */
public interface EstablishRegisteredAuthenticationUseCase {

    AuthenticatedSubject afterRegistration(AuthenticatedSubject registered);
}
