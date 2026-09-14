package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 비밀번호 없는 가입 명령.
 *
 * <p>{@code RegisterWithPasswordCommand} 와 견주면 두 가지가 다르다 — {@code password} 와
 * {@code loginId} 가 없고 {@code verificationCode} 가 있다. 정할 비밀번호가 없고, 이메일이
 * 곧 식별자이며, 그 주소의 주인임을 인증번호가 증명한다.
 */
public record RegisterWithEmailCommand(
        Realm realm,
        String email,
        String name,
        String phoneNumber,
        String verificationCode
) {
}
