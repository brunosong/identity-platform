package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.EmailOtpAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;

public interface EstablishEmailOtpAuthenticationUseCase {
    AuthenticatedSubject withEmailOtp(EmailOtpAuthCommand command);
}
