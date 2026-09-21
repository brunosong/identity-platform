package com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 인가 요청의 원본 값. 주소창에 실려 온 그대로다.
 *
 * <p>realm 만 경로에서 온다. 나머지는 앱이 적어 보낸 값이라 하나도 믿지 않는다.
 */
public record AuthorizationRequestCommand(Realm realm, String responseType, String clientId,
                                          String redirectUri, String scope, String state,
                                          String codeChallenge, String codeChallengeMethod,
                                          String nonce) {
}
