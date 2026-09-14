package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithEmailCommand;

/**
 * 비밀번호 없는 셀프 가입 — 이메일 인증번호로 주소를 확인하고 신원을 만든다.
 *
 * <p>{@link RegisterWithPasswordUseCase} 와 나란히 있다. 만드는 것이 다르다 —
 * 저쪽은 이메일 계정 + 비밀번호 계정을, 이쪽은 이메일 계정만 만든다.
 *
 * <p>토큰은 내주지 않는다. 가입과 로그인은 별개의 요청이라는 규칙을 두 방식이 똑같이 따른다.
 */
public interface RegisterWithEmailUseCase {

    /** 만들어진(또는 이미 있던) 신원의 principalId. */
    String register(RegisterWithEmailCommand command);
}
