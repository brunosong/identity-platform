package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;

/**
 * 소셜 provider 가 확인해 준 신원으로 인증을 성립시키고 <b>거기서 멈춘다.</b> 토큰은 발급하지 않는다.
 *
 * <p>구글에서 돌아온 브라우저를 auth 가 받는 자리(브로커)가 쓴다. 인증이 끝나면 로그인 세션을
 * 심고 원래 인가 요청으로 되돌린다. 토큰은 그 뒤 앱이 code 를 바꿀 때 나간다.
 */
public interface EstablishSocialAuthenticationUseCase {

    AuthenticatedSubject withSocial(SocialAuthCommand command);
}
