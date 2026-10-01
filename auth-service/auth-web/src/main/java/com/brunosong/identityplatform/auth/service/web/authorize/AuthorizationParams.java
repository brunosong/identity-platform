package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.AuthorizationRequestCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.web.bind.annotation.BindParam;

/**
 * 인가 요청이 들고 다니는 값 일곱. 앱이 처음 보낸 주소에도, 로그인 화면과 가입 화면의 숨은 칸에도,
 * 구글로 떠나는 버튼에도 같은 이름으로 실린다.
 *
 * <p>화면 사이를 오가는 값이라 브라우저에서 고칠 수 있다. 그래서 받는 쪽마다 다시 검증한다
 * ({@code StartAuthorizationUseCase}). 이 객체는 값을 모아 줄 뿐 믿을 만하다고 말하지 않는다.
 *
 * <p>이름은 OAuth 그대로(snake_case)다. 컨트롤러 인자에 객체로 받으면 스프링이 그 이름으로 채운다.
 */
public record AuthorizationParams(
        @BindParam("client_id") String clientId,
        @BindParam("redirect_uri") String redirectUri,
        String scope,
        String state,
        @BindParam("code_challenge") String codeChallenge,
        @BindParam("code_challenge_method") String codeChallengeMethod,
        String nonce) {

    /** 화면을 거쳐 온 요청. 앱이 처음 보낸 요청이 code 흐름일 때만 화면이 열리므로 종류는 code 다. */
    public AuthorizationRequestCommand toCommand(Realm realm) {
        return toCommand(realm, "code");
    }

    /** 앱이 처음 보낸 요청. 종류도 앱이 적어 보낸 값으로 검증한다. */
    public AuthorizationRequestCommand toCommand(Realm realm, String responseType) {
        return new AuthorizationRequestCommand(realm, responseType, clientId, redirectUri, scope, state,
                codeChallenge, codeChallengeMethod, nonce);
    }
}
