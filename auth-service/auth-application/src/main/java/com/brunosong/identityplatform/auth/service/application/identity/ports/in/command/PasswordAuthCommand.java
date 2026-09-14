package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 아이디/비밀번호 로그인 명령.
 *
 * <p>realm 을 함께 받는다({@code EmailOtpAuthCommand} 와 같은 이유다). loginId 만으로 자격증명을 찾으면
 * 상대 realm 의 계정이 걸려, 고객 아이디/비밀번호로 직원 realm 토큰이 나갈 수 있다.

 * <p>{@code clientId} 는 토큰을 받아 갈 앱이다. 그 값이 토큰의 {@code aud} 를 정하므로,
 * 나열되지 않은 서비스는 이 토큰을 거부한다.
 */
public record PasswordAuthCommand(Realm realm, String loginId, String password, String clientId) {
}
