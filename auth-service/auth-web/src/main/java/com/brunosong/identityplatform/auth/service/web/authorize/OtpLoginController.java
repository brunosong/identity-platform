package com.brunosong.identityplatform.auth.service.web.authorize;


import com.brunosong.identityplatform.auth.service.application.identity.ports.in.EstablishEmailOtpAuthenticationUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.EmailOtpAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidAuthorizationRequestException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.AuthorizationRequestCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequiredArgsConstructor
public class OtpLoginController {

    private final AuthenticationRealm authenticationRealm;
    private final StartAuthorizationUseCase startAuthorization;
    private final RequestEmailOtpUseCase requestEmailOtp;
    private final EstablishEmailOtpAuthenticationUseCase establishEmailOtpAuthentication;
    private final LoginSessionStarter loginSessionStarter;
    private final AuthorizationCodeRedirect authorizationCodeRedirect;

    /**
     * 인증번호를 보낸다.
     *
     * <p>보냈는지 아닌지를 화면에 나눠 보여주지 않는다. 등록되지 않은 주소든 재발송 쿨다운에
     * 걸렸든 같은 화면으로 답한다. 갈라 보여주면 주소를 넣어보는 것만으로 누가 가입돼 있는지
     * 훑을 수 있다. 무엇을 보낼지 말지는 {@link RequestEmailOtpUseCase} 안에서 판단한다.
     */
    @PostMapping("/realms/{realm}/auth/send-code")
    public ModelAndView sendCode(@PathVariable String realm,
                                 @RequestParam(name = "client_id", required = false) String clientId,
                                 @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                 @RequestParam(required = false) String scope,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                                 @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
                                 @RequestParam(required = false) String nonce,
                                 @RequestParam(required = false) String email,
                                 HttpServletResponse response) {
        Realm resolved;
        AuthorizationRequest request;
        try {
            // 경로에 적힌 realm 을 확인한다. 모르는 realm 이면 여기서 끝난다.
            resolved = authenticationRealm.of(realm);

            // 이 인가 요청을 받아들일 수 있는지 다시 본다. 등록된 앱인가, 등록된 주소인가,
            // PKCE 가 붙었는가. 화면의 hidden 값으로 온 값들이라 브라우저에서 고칠 수 있다.

            request = startAuthorization.start(new AuthorizationRequestCommand(
                    resolved, "code", clientId, redirectUri, scope, state,
                    codeChallenge, codeChallengeMethod, nonce));

        } catch (NotFoundException | InvalidAuthorizationRequestException | IllegalArgumentException e) {
            // 돌아갈 주소를 믿을 수 없는 상태다. 적혀 온 주소로 보내지 않고 우리 화면에서 끝낸다.
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return new ModelAndView("oauth/error").addObject("reason", e.getMessage());
        }

        // 인증번호를 보낸다. 보낼지 말지는 유스케이스가 판단한다 - 등록되지 않은 주소나
        // 재발송 쿨다운이면 그 안에서 조용히 넘어간다. 여기서 잡아 가르지 않는다.
        requestEmailOtp.request(resolved, email);

        // 보냈든 아니든 같은 화면으로 답한다. 인증번호 칸이 열린 상태로 다시 그린다.
        return otpScreen(realm, request, email, null);
    }

    /**
     * 인증번호를 확인하고 로그인을 끝낸다.
     *
     * <p>비밀번호 경로와 마지막 두 걸음이 같다. 세션을 심고, 코드를 발급해 앱으로 돌려보낸다.
     * 사람을 확인한 방법만 달랐을 뿐이라 그 뒤는 같아야 한다.
     */
    @PostMapping("/realms/{realm}/auth/login/otp")
    public ModelAndView login(@PathVariable String realm,
                              @RequestParam(name = "client_id", required = false) String clientId,
                              @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                              @RequestParam(required = false) String scope,
                              @RequestParam(required = false) String state,
                              @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                              @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
                              @RequestParam(required = false) String nonce,
                              @RequestParam(required = false) String email,
                              @RequestParam(required = false) String code,
                              HttpServletRequest httpRequest, HttpServletResponse response) {
        Realm resolved;
        AuthorizationRequest request;
        try {
            // 경로에 적힌 realm 을 확인한다. 모르는 realm 이면 여기서 끝난다.
            resolved = authenticationRealm.of(realm);

            // 이 인가 요청을 받아들일 수 있는지 다시 본다. hidden 으로 온 값이라 고칠 수 있다.
            request = startAuthorization.start(new AuthorizationRequestCommand(
                    resolved, "code", clientId, redirectUri, scope, state,
                    codeChallenge, codeChallengeMethod, nonce));
        } catch (NotFoundException | InvalidAuthorizationRequestException | IllegalArgumentException e) {
            // 돌아갈 주소를 믿을 수 없는 상태다. 적혀 온 주소로 보내지 않고 우리 화면에서 끝낸다.
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return new ModelAndView("oauth/error").addObject("reason", e.getMessage());
        }

        AuthenticatedSubject subject;
        try {
            // 인증번호를 확인한다. 여기까지가 "이 사람이 누구인가" 다. 토큰은 나오지 않는다.
            subject = establishEmailOtpAuthentication.withEmailOtp(
                    new EmailOtpAuthCommand(resolved, email, code));
        } catch (AuthenticationFailedException e) {
            // 틀렸다고 앱에 알리지 않는다. 인증번호 칸이 열린 채로 화면을 다시 그린다.
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return otpScreen(realm, request, email, e.getMessage());
        }

        // 이 브라우저가 로그인했다는 사실을 남긴다. 다음 앱은 이 쿠키로 화면을 건너뛴다.
        loginSessionStarter.start(subject, httpRequest, response);

        // 인가 코드를 발급해 등록된 주소로 303. 실려 나가는 것은 code 와 state 뿐이다.
        return authorizationCodeRedirect.issueAndRedirect(request, subject);
    }

    /**
     * 인증번호 칸이 열린 상태로 로그인 화면을 그린다.
     *
     * <p>인가 요청 값을 그대로 실어야 사람이 한 번 더 제출할 수 있다. 이메일도 다시 싣는다.
     * 코드를 받아 적고 돌아왔는데 주소를 다시 치게 만들 이유가 없다.
     */
    private static ModelAndView otpScreen(String realm, AuthorizationRequest request,
                                          String email, String error) {
        return new ModelAndView("oauth/login")
                .addObject("realm", realm.toLowerCase())
                .addObject("registrationOpen", Realm.valueOf(realm.toUpperCase()).allowsSelfRegistration())
                .addObject("request", request)
                .addObject("otpSent", true)
                .addObject("email", email)
                .addObject("error", error);
    }

}
