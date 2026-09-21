package com.brunosong.identityplatform.auth.service.application.oauth.ports.in;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.RegisterOAuthClientCommand;
import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;

public interface RegisterOAuthClientUseCase {

    OAuthClient register(RegisterOAuthClientCommand command);
}
