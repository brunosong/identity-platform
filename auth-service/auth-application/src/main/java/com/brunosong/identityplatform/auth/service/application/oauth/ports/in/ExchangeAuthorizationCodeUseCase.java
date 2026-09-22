package com.brunosong.identityplatform.auth.service.application.oauth.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.ExchangeAuthorizationCodeCommand;

/**
 * 인가 코드를 토큰으로 바꾼다. 로그인 흐름의 마지막 걸음이다.
 *
 * <p>앞의 두 요청과 달리 <b>사람이 아니라 앱이 부른다.</b> 브라우저 화면이 오가는 자리가 아니라
 * 서버끼리 주고받는 모양이라, 결과도 화면이 아니라 JSON 이다.
 *
 * <p>돌려주는 것은 기존 로그인 API 와 같은 {@link AuthenticationResult} 다. 여기까지 왔다는 것은
 * 이미 인증이 성립했다는 뜻이고, 그 사람 앞으로 토큰을 내주는 일은 어느 경로로 왔든 같다.
 *
 * <p>실패는 사유를 나누지 않는다. 없는 코드든, 만료된 코드든, 남의 코드든 같은 실패다 -
 * 어느 쪽인지 알려주면 코드를 주운 쪽이 그것으로 무엇이 틀렸는지 좁혀갈 수 있다.
 */
public interface ExchangeAuthorizationCodeUseCase {

    AuthenticationResult exchange(ExchangeAuthorizationCodeCommand command);
}
