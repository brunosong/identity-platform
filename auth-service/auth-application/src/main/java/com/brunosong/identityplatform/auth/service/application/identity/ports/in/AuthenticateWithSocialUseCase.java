package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;

/**
 * 소셜 로그인 인바운드 포트. 가입/로그인이 통합된 upsert 다 — 이미 연결된 소셜이면 그 Principal 로,
 * 처음이면 verified email 로 같은 주체를 찾아(없으면 생성) 그 Principal 에 SocialAccount 를 연결하고 발급한다.
 */
public interface AuthenticateWithSocialUseCase {

    AuthenticationResult authenticate(SocialAuthCommand command);
}
