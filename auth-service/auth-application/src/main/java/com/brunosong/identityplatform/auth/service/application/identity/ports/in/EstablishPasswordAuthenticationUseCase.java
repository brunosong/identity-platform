package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;

/**
 * 인증을 성립시키고 <b>거기서 멈춘다.</b> 토큰은 발급하지 않는다.
 *
 * <p>로그인 화면이 쓴다. 사람이 비밀번호를 적는 시점과 앱이 토큰을 받아가는 시점이 갈리는
 * 경로이기 때문이다. 그 사이는 인가 코드 한 장이 잇는다.
 *
 * <p>인증이 성립했다는 사실(인증 시각, 인증 이벤트)은 여기서 남는다. 사람이 우리 앞에서
 * 로그인한 것은 토큰이 나가는 것과 별개의 사건이고, 그 시점도 여기가 맞다.
 *
 * <p>자격증명을 받은 자리에서 토큰까지 내주는 길(ROPC)은 없다. 비밀번호가 앱을 거치게 되고,
 * 로그인 절차를 바꿀 때마다 앱을 다시 배포해야 한다. 인증번호({@link EstablishEmailOtpAuthenticationUseCase})와
 * 소셜({@link EstablishSocialAuthenticationUseCase})도 같은 자리에서 멈춘다.
 */
public interface EstablishPasswordAuthenticationUseCase {

    AuthenticatedSubject withPassword(PasswordAuthCommand command);
}
