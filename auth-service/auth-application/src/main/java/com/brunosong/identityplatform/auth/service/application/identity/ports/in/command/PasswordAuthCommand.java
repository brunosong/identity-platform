package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 아이디/비밀번호 로그인 명령.
 *
 * <p>realm 을 함께 받는다({@code EmailOtpAuthCommand} 와 같은 이유다). loginId 만으로 자격증명을 찾으면
 * 상대 realm 의 계정이 걸려, 고객 아이디/비밀번호로 직원 realm 토큰이 나갈 수 있다.

 */
public record PasswordAuthCommand(Realm realm, String loginId, String password) {
}
