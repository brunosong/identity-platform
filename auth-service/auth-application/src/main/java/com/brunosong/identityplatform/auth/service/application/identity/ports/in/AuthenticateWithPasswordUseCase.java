package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;

/**
 * 아이디/비밀번호 로그인 인바운드 포트. (brunosong AuthenticateWithPassword 참고)
 */
public interface AuthenticateWithPasswordUseCase {

    /** 실패 시 {@link com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException}. */
    AuthenticationResult authenticate(PasswordAuthCommand command);
}
