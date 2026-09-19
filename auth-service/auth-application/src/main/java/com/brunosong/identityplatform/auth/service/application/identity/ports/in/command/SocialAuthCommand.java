package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.identity.SocialCallback;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 소셜 로그인 커맨드. realm 은 이 로그인이 어느 주체 도메인인지(portal=CUSTOMER),
 * authorizationCode 는 provider 콜백에서 받은 인가 코드다.
 *
 * <p>{@code callback} 은 provider 가 브라우저를 어디로 돌려보냈는지다({@link SocialCallback}).
 * 같은 code 라도 어느 주소로 받았느냐에 따라 교환 요청이 달라진다.
 */
public record SocialAuthCommand(Realm realm, SocialProvider provider, String authorizationCode,
                                SocialCallback callback) {
}
