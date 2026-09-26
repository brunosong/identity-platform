package com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 인가 코드를 토큰으로 바꾼다. 앱이 보내온 값 그대로다.
 *
 * <p>code 말고 셋이 더 붙는 이유는, 코드 한 장만으로는 <b>들고 온 쪽이 임자인지</b> 알 수 없기
 * 때문이다. 발급할 때 적어 둔 값과 하나씩 맞춰본다.
 *
 * <ul>
 *   <li>{@code clientId} - 시작한 앱과 같은 앱인가</li>
 *   <li>{@code redirectUri} - 시작할 때 적어 보낸 주소와 같은가(RFC 6749 4.1.3)</li>
 *   <li>{@code codeVerifier} - PKCE 원본. 해시해서 발급 때 받아둔 값과 맞춘다</li>
 *   <li>{@code clientSecret} - 시크릿이 있는 앱만 낸다. 없는 앱은 비워 둔다</li>
 * </ul>
 *
 * <p>시크릿은 폼 본문으로만 받는다({@code client_secret_post}). {@code Authorization: Basic}
 * 헤더는 읽지 않는다.
 */
public record ExchangeAuthorizationCodeCommand(Realm realm, String code, String clientId,
                                               String redirectUri, String codeVerifier,
                                               String clientSecret) {
}
