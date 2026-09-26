package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithEmailCommand;

/**
 * 비밀번호 없는 셀프 가입 — 이메일 인증번호로 주소를 확인하고 신원을 만든다.
 *
 * <p>{@link RegisterWithPasswordUseCase} 와 나란히 있다. 만드는 것이 다르다 —
 * 저쪽은 이메일 계정 + 비밀번호 계정을, 이쪽은 이메일 계정만 만든다.
 *
 * <p>인증번호를 받아낸 것이 곧 그 주소의 주인이라는 증명이다. 로그인은 시키지 않는다. 가입 뒤
 * 로그인할지는 입구가 정한다({@link EstablishRegisteredAuthenticationUseCase}).
 */
public interface RegisterWithEmailUseCase {

    /** 만들어진(또는 이미 있던) 신원. */
    AuthenticatedSubject register(RegisterWithEmailCommand command);
}
