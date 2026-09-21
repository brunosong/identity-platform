package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.EstablishAuthenticationUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidAuthorizationRequestException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.IssueAuthorizationCodeUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.AuthorizationRequestCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.IssueAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 로그인 화면이 제출한 폼 - {@code POST /realms/{realm}/auth/login}.
 *
 * <p>여기서 로그인 흐름의 앞쪽이 끝난다. 사람을 확인하고, 인가 코드를 만들어, 앱 주소로
 * 돌려보낸다. 앱이 그 코드를 토큰으로 바꾸는 것은 다음 요청이고 이 컨트롤러와 상관이 없다.
 *
 * <h2>인가 요청을 다시 검증한다</h2>
 * 앱 이름과 돌아갈 주소가 화면의 hidden 값으로 실려 온다. 브라우저에서 고칠 수 있는 값이다.
 * 그래서 {@link AuthorizationEndpointController} 가 처음에 한 검증을 여기서 한 번 더 한다 -
 * 등록된 앱인지, 등록된 주소인지. 고쳐 보내봐야 통과하지 못한다.
 *
 * <p>화면이 값을 들고 있는 방식(hidden) 대신 서버 세션에 넣어두는 방법도 있다. 그쪽이 Keycloak
 * 이 하는 일이고 값을 고칠 여지 자체가 없어진다. 다만 세션이 아직 없다. 세션이 생기면 그때
 * 옮긴다 - 검증을 두 번 하는 지금 구조는 그때까지도 그대로 남는다.
 *
 * <h2>비밀번호가 틀리면 돌려보내지 않는다</h2>
 * 로그인 화면을 다시 그린다. 앱은 사람이 몇 번 틀렸는지 알 필요가 없다. 다시 그릴 때 인가 요청
 * 값을 그대로 실어 보내야 사람이 한 번 더 제출할 수 있다.
 */
@Controller
@RequiredArgsConstructor
public class LoginSubmissionController {

    private final AuthenticationRealm authenticationRealm;
    private final StartAuthorizationUseCase startAuthorization;
    private final EstablishAuthenticationUseCase establishAuthentication;
    private final IssueAuthorizationCodeUseCase issueAuthorizationCode;

    @PostMapping("/realms/{realm}/auth/login")
    public ModelAndView login(@PathVariable String realm,
                              @RequestParam(name = "client_id", required = false) String clientId,
                              @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                              @RequestParam(required = false) String scope,
                              @RequestParam(required = false) String state,
                              @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                              @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
                              @RequestParam(required = false) String nonce,
                              @RequestParam(required = false) String loginId,
                              @RequestParam(required = false) String password,
                              HttpServletResponse response) {
        AuthorizationRequest request;
        try {
            request = startAuthorization.start(new AuthorizationRequestCommand(
                    authenticationRealm.of(realm), "code", clientId, redirectUri, scope, state,
                    codeChallenge, codeChallengeMethod, nonce));
        } catch (NotFoundException | InvalidAuthorizationRequestException | IllegalArgumentException e) {
            // 돌아갈 주소를 믿을 수 없는 상태다. 적혀 온 주소로 보내지 않고 우리 화면에서 끝낸다.
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return new ModelAndView("oauth/error").addObject("reason", e.getMessage());
        }

        AuthenticatedSubject subject;
        try {
            subject = establishAuthentication.withPassword(
                    new PasswordAuthCommand(authenticationRealm.of(realm), loginId, password));
        } catch (AuthenticationFailedException e) {
            return loginScreenWithError(realm, request, e.getMessage(), response);
        }

        AuthorizationCode code = issueAuthorizationCode.issue(new IssueAuthorizationCodeCommand(
                request, subject.realm(), subject.principalId()));

        return new ModelAndView(redirectWithCode(request, code));
    }

    /**
     * 코드와 state 를 주소창에 실어 앱으로 돌려보낸다. <b>토큰은 여기 없다.</b>
     *
     * <p>303 을 쓴다. 302 도 브라우저는 GET 으로 따라가지만 그것은 관행이고, POST 뒤에 쓰라고
     * 명세에 적힌 것은 303 이다.
     *
     * <p>state 는 앱이 시작할 때 준 값을 그대로 돌려준다. 앱은 그것으로 자기가 시작한 로그인이
     * 맞는지 확인한다 - 남이 시작한 로그인의 콜백을 열게 만드는 공격을 여기서 거른다.
     */
    private static RedirectView redirectWithCode(AuthorizationRequest request, AuthorizationCode code) {
        UriComponentsBuilder location = UriComponentsBuilder.fromUriString(request.getRedirectUri())
                .queryParam("code", code.getCode());
        if (request.getState() != null) {
            location.queryParam("state", request.getState());
        }

        RedirectView redirect = new RedirectView(location.encode().toUriString());
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        return redirect;
    }

    /** 로그인 화면을 다시 그린다. 인가 요청 값을 그대로 실어야 사람이 한 번 더 제출할 수 있다. */
    private static ModelAndView loginScreenWithError(String realm, AuthorizationRequest request,
                                                     String message, HttpServletResponse response) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return new ModelAndView("oauth/login")
                .addObject("realm", realm.toLowerCase())
                .addObject("request", request)
                .addObject("error", message);
    }
}
