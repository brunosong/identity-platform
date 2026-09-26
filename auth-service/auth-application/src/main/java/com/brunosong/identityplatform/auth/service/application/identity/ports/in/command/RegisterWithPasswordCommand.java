package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 가입(주체 프로비저닝 + 자격증명) 명령. auth 가 realm 에 따라 대상 BC 에 주체를 생성하게 하고
 * Principal + 로컬 자격증명(아이디/비번)을 만든다.
 *
 * <p>{@code verificationCode} 는 그 이메일로 보낸 가입용 인증번호다. 비밀번호 가입도 이메일의 주인임을
 * 먼저 증명해야 한다. 확인하지 않은 주소로 만든 계정은 남의 주소일 수 있다.
 */
public record RegisterWithPasswordCommand(
        Realm realm,
        String email,
        String name,
        String phoneNumber,
        String loginId,
        String password,
        String verificationCode
) {
}
