package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.EstablishPasswordAuthenticationUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.StartLoginSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.LoginSession;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.LoginSessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.time.Instant;

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
 * <h2>여기서 로그인 세션이 시작된다</h2>
 * 자격증명이 확인되면 세션을 만들어 쿠키로 심는다. 그 쿠키가 다음 앱의 인가 요청에 실려 오면
 * 로그인 화면을 건너뛴다. 통합 로그인이 성립하는 것이 이 한 줄이다.
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
    private final EstablishPasswordAuthenticationUseCase establishAuthentication;
    private final StartLoginSessionUseCase startLoginSession;
    private final AuthorizationCodeRedirect authorizationCodeRedirect;
    private final LoginSessionStarter loginSessionStarter;

    @PostMapping("/realms/{realm}/auth/login")
    public ModelAndView login(@PathVariable String realm,
                              AuthorizationParams params,
                              @RequestParam(required = false) String loginId,
                              @RequestParam(required = false) String password,
                              HttpServletRequest httpRequest, HttpServletResponse response) {
        // 화면의 숨은 칸으로 온 값이라 다시 검증한다. 받아줄 수 없으면 AuthorizationErrorScreen 이 끝낸다.
        AuthorizationRequest request = startAuthorization.start(params.toCommand(authenticationRealm.of(realm)));

        AuthenticatedSubject subject;
        try {
            subject = establishAuthentication.withPassword(
                    new PasswordAuthCommand(authenticationRealm.of(realm), loginId, password));
        } catch (AuthenticationFailedException e) {
            return loginScreenWithError(realm, request, e.getMessage(), response);
        }

        loginSessionStarter.start(subject, httpRequest, response);

        return authorizationCodeRedirect.issueAndRedirect(request, subject);
    }

    /** 로그인 화면을 다시 그린다. 인가 요청 값을 그대로 실어야 사람이 한 번 더 제출할 수 있다. */
    private static ModelAndView loginScreenWithError(String realm, AuthorizationRequest request,
                                                     String message, HttpServletResponse response) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return new ModelAndView("oauth/login")
                .addObject("realm", realm.toLowerCase())
                .addObject("registrationOpen", Realm.valueOf(realm.toUpperCase()).allowsSelfRegistration())
                .addObject("request", request)
                .addObject("error", message);
    }
}
