package com.brunosong.identityplatform.auth.service.application.oauth.ports.in.result;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;

/**
 * 코드를 바꿔 받은 것. 다른 로그인 경로와 같은 토큰에, 코드 교환에서만 나오는 id_token 이 붙는다.
 *
 * <p>id_token 을 {@link AuthenticationResult} 에 넣지 않는 이유는, 그것이 로그인 경로 전부가
 * 함께 쓰는 값이기 때문이다. id_token 은 인가 요청이 {@code openid} 를 달라고 했을 때만 생기고,
 * 그 요청이 있는 곳은 코드 교환뿐이다.
 *
 * @param idToken {@code openid} 를 요청하지 않았으면 비어 있다
 */
public record ExchangedTokens(AuthenticationResult authentication, String idToken) {
}
