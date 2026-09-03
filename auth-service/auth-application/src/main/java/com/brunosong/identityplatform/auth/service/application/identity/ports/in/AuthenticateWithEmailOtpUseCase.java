package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.EmailOtpAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;

/**
 * 이메일 OTP 로그인 인바운드 포트 — Principal 모델. admin(EMPLOYEE)이 사용한다.
 *
 * <p>password 로그인과 같은 결과(AuthenticationResult)를 내지만 자격증명 방식만 OTP 다.
 * OTP 검증·주체 해석은 OtpCredentialVerifier(호스트), 토큰은 TokenIssuerPort(호스트)에 위임한다.
 */
public interface AuthenticateWithEmailOtpUseCase {

    /** 실패 시 {@link com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException}. */
    AuthenticationResult authenticate(EmailOtpAuthCommand command);
}
