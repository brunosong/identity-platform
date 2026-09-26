package com.brunosong.identityplatform.auth.service.application.oauth.ports.in.result;

import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;

/**
 * 등록한 앱과, 시크릿을 붙였다면 그 원문.
 *
 * <p>원문이 밖으로 나가는 곳은 여기 한 번뿐이다. 저장소에는 해시만 남으므로 이 값을 놓치면
 * 다시 볼 방법이 없고, 새로 발급해야 한다. public client 면 {@code secret} 은 비어 있다.
 */
public record RegisteredOAuthClient(OAuthClient client, String secret) {
}
