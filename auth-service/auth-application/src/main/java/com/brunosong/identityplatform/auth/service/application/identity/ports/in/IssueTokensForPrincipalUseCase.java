package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;

/**
 * 이미 인증이 성립한 신원 앞으로 토큰을 발급한다.
 *
 * <p>인증 절차가 없다. 이 입구를 부르는 쪽은 인증이 <b>다른 시점에</b> 끝났다는 것을 이미
 * 확인한 상태다. 지금은 인가 코드를 토큰으로 바꾸는 자리가 쓴다 - 사람은 1분 전 로그인 화면에서
 * 확인됐고, 그 사실이 코드에 적혀 있다.
 *
 * <p><b>realm 을 받지 않는다.</b> 토큰의 realm 은 요청이 아니라 신원 자신이 정한다. 부르는 쪽이
 * realm 을 함께 넘기면 인증된 주체와 다른 realm 의 토큰이 나갈 여지가 생긴다.
 *
 * <p>인증을 거치지 않고 토큰을 내주는 입구라 아무나 부르면 안 된다. 그래서 {@code PrincipalId} 를
 * 받는다 - 요청에 실려 오는 값이 아니라 우리가 발급물(인가 코드)에 적어 둔 값이다.
 */
public interface IssueTokensForPrincipalUseCase {

    AuthenticationResult forPrincipal(PrincipalId principalId);
}
