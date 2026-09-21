package com.brunosong.identityplatform.auth.service.application.oauth.ports.in;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.AuthorizationRequestCommand;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;

/**
 * 인가 요청을 받아들일 수 있는지 보고, 로그인 화면으로 넘길 요청을 돌려준다.
 *
 * <p>여기서 통과한 요청만 사람에게 비밀번호를 묻는다. 순서가 반대면(먼저 묻고 나중에 확인)
 * 등록되지 않은 앱이 우리 로그인 화면을 띄워 비밀번호를 받아내는 자리가 생긴다.
 */
public interface StartAuthorizationUseCase {

    AuthorizationRequest start(AuthorizationRequestCommand command);
}
