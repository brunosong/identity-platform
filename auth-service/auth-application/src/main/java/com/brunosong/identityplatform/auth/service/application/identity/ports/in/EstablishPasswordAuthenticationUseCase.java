package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;

/**
 * 인증을 성립시키고 <b>거기서 멈춘다.</b> 토큰은 발급하지 않는다.
 *
 * <p>로그인 화면이 쓴다. 사람이 비밀번호를 적는 시점과 앱이 토큰을 받아가는 시점이 갈리는
 * 경로이기 때문이다. 그 사이는 인가 코드 한 장이 잇는다.
 *
 * <p>{@link AuthenticateWithPasswordUseCase} 와 하는 일이 겹쳐 보이지만 끝나는 자리가 다르다.
 * 저쪽은 자격증명을 받은 그 자리에서 토큰까지 내주고(ROPC), 이쪽은 "누가 로그인했는가" 까지만
 * 확정한다. 토큰을 내줄지 말지는 부르는 쪽이 정하는 것이 아니라 <b>어느 경로인지가</b> 정한다.
 *
 * <p>인증이 성립했다는 사실(인증 시각, 인증 이벤트)은 여기서 남는다. 사람이 우리 앞에서
 * 로그인한 것은 토큰이 나가는 것과 별개의 사건이고, 그 시점도 여기가 맞다.
 *
 * <p>지금은 비밀번호뿐이다. 이메일 인증번호나 소셜로 들어오는 길도 결국 같은 자리로 모이지만,
 * 그 경로를 code 흐름에 붙일 때 메서드를 늘린다.
 */
public interface EstablishAuthenticationUseCase {

    AuthenticatedSubject withPassword(PasswordAuthCommand command);
}
