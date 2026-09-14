package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 소셜 로그인 커맨드. realm 은 이 로그인이 어느 주체 도메인인지(portal=CUSTOMER),
 * authorizationCode 는 provider 콜백에서 받은 인가 코드다.

 * <p>{@code clientId} 는 토큰을 받아 갈 앱이다. 그 값이 토큰의 {@code aud} 를 정하므로,
 * 나열되지 않은 서비스는 이 토큰을 거부한다.
 */
public record SocialAuthCommand(Realm realm, SocialProvider provider, String authorizationCode,
                                String clientId) {
}
