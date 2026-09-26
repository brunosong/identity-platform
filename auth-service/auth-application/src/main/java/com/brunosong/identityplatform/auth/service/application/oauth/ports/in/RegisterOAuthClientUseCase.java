package com.brunosong.identityplatform.auth.service.application.oauth.ports.in;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.RegisterOAuthClientCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.result.RegisteredOAuthClient;

public interface RegisterOAuthClientUseCase {

    RegisteredOAuthClient register(RegisterOAuthClientCommand command);
}
