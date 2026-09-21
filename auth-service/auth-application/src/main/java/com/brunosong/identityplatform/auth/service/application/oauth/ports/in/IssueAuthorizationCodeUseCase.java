package com.brunosong.identityplatform.auth.service.application.oauth.ports.in;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.IssueAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;

/**
 * 로그인이 성립한 직후 인가 코드를 발급한다.
 *
 * <p>이 자리가 인증과 토큰 발급 사이를 잇는다. 사람은 방금 우리 앞에서 로그인했고, 앱은 아직
 * 아무것도 받지 못했다. 그 사이에 건네는 것이 이 코드 한 장이다.
 *
 * <p>발급은 저장까지다. 코드를 만들어 놓고 저장하지 않으면 앱이 그것을 들고 왔을 때 우리가
 * 모르는 값이 된다. 만드는 일과 적어 두는 일을 호출자가 나눠 부르게 하지 않는다.
 *
 * <p>여기서 돌아가는 코드를 어디로 어떻게 보낼지는 부르는 쪽(웹)이 정한다. 주소창에 실어
 * 리다이렉트하는 것은 HTTP 의 일이지 이 유스케이스의 일이 아니다.
 */
public interface IssueAuthorizationCodeUseCase {

    AuthorizationCode issue(IssueAuthorizationCodeCommand command);
}
