package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithEmailUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestRegistrationOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithEmailCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidAuthorizationRequestException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.AuthorizationRequestCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.domain.shared.RegistrationMethod;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * 가입 화면 - {@code /realms/{realm}/auth/register}.
 *
 * <p>로그인 화면처럼 auth 가 그리고, 인가 요청을 들고 온다. 가입이 끝나면 그 사람으로 로그인된
 * 세션을 심고 code 를 앱에 돌려준다. 앱이 보기에는 로그인과 끝이 같다. 가입 폼도 비밀번호도
 * 앱을 거치지 않는다.
 *
 * <h2>두 걸음이다</h2>
 * <pre>
 * 1. 이메일, 이름, 전화번호        POST /auth/register/send-code   가입용 인증번호를 보낸다
 * 2. 인증번호, 비밀번호(선택)      POST /auth/register             가입하고 로그인까지 끝낸다
 * </pre>
 * 비밀번호로 가입해도 인증번호를 먼저 받는다. 확인하지 않은 주소로 계정을 만들면 그 주소는 남의 것일
 * 수 있다. 비밀번호는 두 번째 걸음에서 받는다. 첫 걸음에서 받으면 화면 사이를 hidden 값으로 들고
 * 다녀야 한다.
 *
 * <p>어떤 가입을 열지는 realm 이 정한다({@link Realm#registrationMethods()}). 인증번호 가입이 열려
 * 있어야 가입할 수 있고, 비밀번호 가입이 열려 있으면 두 번째 걸음에 비밀번호 칸이 생긴다. 포털은
 * 둘 다 열려 있고 어드민은 닫혀 있다. 닫힌 realm 의 폼이 들어오면 404 화면이다.
 *
 * <p>인가 요청은 요청마다 다시 검증한다. hidden 으로 오가는 값이라 브라우저에서 고칠 수 있다.
 */
@Controller
@RequiredArgsConstructor
public class RegistrationController {

    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 64;

    private final AuthenticationRealm authenticationRealm;
    private final StartAuthorizationUseCase startAuthorization;
    private final RegisterWithPasswordUseCase registerWithPassword;
    private final RequestRegistrationOtpUseCase requestRegistrationOtp;
    private final RegisterWithEmailUseCase registerWithEmail;
    private final LoginSessionStarter loginSessionStarter;
    private final AuthorizationCodeRedirect authorizationCodeRedirect;

    /** 가입 화면을 그린다. 로그인 화면의 "회원가입" 이 인가 요청을 그대로 들고 온다. */
    @GetMapping("/realms/{realm}/auth/register")
    public ModelAndView screen(@PathVariable String realm,
                               @RequestParam(name = "client_id", required = false) String clientId,
                               @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                               @RequestParam(required = false) String scope,
                               @RequestParam(required = false) String state,
                               @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                               @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
                               @RequestParam(required = false) String nonce,
                               HttpServletResponse response) {
        try {
            Realm resolved = requireAnyRegistration(realm);
            AuthorizationRequest request = startAuthorization.start(new AuthorizationRequestCommand(
                    resolved, "code", clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, nonce));
            return registerScreen(resolved, request);
        } catch (NotFoundException e) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return errorScreen(e.getMessage());
        } catch (InvalidAuthorizationRequestException | IllegalArgumentException e) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return errorScreen(e.getMessage());
        }
    }

    /**
     * 가입용 인증번호를 보낸다.
     *
     * <p>보냈는지 아닌지를 화면에 나눠 보여주지 않는다. 가입용 번호는 가입되지 않은 주소로만
     * 나가는데, 이미 가입된 주소라서 안 보냈다고 말하면 주소를 넣어보는 것만으로 누가 가입돼
     * 있는지 훑을 수 있다. 보낼지 말지는 {@link RequestRegistrationOtpUseCase} 가 정한다.
     */
    @PostMapping("/realms/{realm}/auth/register/send-code")
    public ModelAndView sendCode(@PathVariable String realm,
                                 @RequestParam(name = "client_id", required = false) String clientId,
                                 @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                 @RequestParam(required = false) String scope,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                                 @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
                                 @RequestParam(required = false) String nonce,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String phoneNumber,
                                 HttpServletResponse response) {
        Realm resolved;
        AuthorizationRequest request;
        try {
            resolved = authenticationRealm.requireSelfRegistration(realm, RegistrationMethod.EMAIL_OTP);
            request = startAuthorization.start(new AuthorizationRequestCommand(
                    resolved, "code", clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, nonce));
        } catch (NotFoundException e) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return errorScreen(e.getMessage());
        } catch (InvalidAuthorizationRequestException | IllegalArgumentException e) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return errorScreen(e.getMessage());
        }

        try {
            requireProfile(email, name);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return registerScreen(resolved, request)
                    .addObject("email", email).addObject("name", name).addObject("phoneNumber", phoneNumber)
                    .addObject("error", e.getMessage());
        }

        requestRegistrationOtp.request(resolved, email.trim());
        return otpScreen(resolved, request, email.trim(), name.trim(), phoneNumber, null);
    }

    /**
     * 가입을 끝낸다. 인증번호를 확인하고, 비밀번호를 적었으면 비밀번호 계정까지 만든다.
     *
     * <p>비밀번호를 비우면 인증번호로만 로그인하는 계정이 된다. 어느 쪽이든 이메일은 확인된 주소다.
     * 가입이 끝나면 그 사람으로 로그인된 세션을 심고 code 를 앱에 돌려준다.
     */
    @PostMapping("/realms/{realm}/auth/register")
    public ModelAndView register(@PathVariable String realm,
                                 @RequestParam(name = "client_id", required = false) String clientId,
                                 @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                 @RequestParam(required = false) String scope,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                                 @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
                                 @RequestParam(required = false) String nonce,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String phoneNumber,
                                 @RequestParam(required = false) String code,
                                 @RequestParam(required = false) String password,
                                 HttpServletRequest httpRequest, HttpServletResponse response) {
        Realm resolved;
        AuthorizationRequest request;
        try {
            resolved = authenticationRealm.requireSelfRegistration(realm, RegistrationMethod.EMAIL_OTP);
            request = startAuthorization.start(new AuthorizationRequestCommand(
                    resolved, "code", clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, nonce));
        } catch (NotFoundException e) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return errorScreen(e.getMessage());
        } catch (InvalidAuthorizationRequestException | IllegalArgumentException e) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return errorScreen(e.getMessage());
        }

        AuthenticatedSubject subject;
        try {
            requireProfile(email, name);
            subject = hasText(password)
                    ? registerWithPassword(resolved, email, name, phoneNumber, code, password)
                    : registerWithEmail.register(new RegisterWithEmailCommand(
                            resolved, email.trim(), name.trim(), blankToNull(phoneNumber), code));
        } catch (IllegalArgumentException | AuthenticationFailedException e) {
            // 번호가 틀렸거나 비밀번호가 규칙에 맞지 않는다. 번호 칸이 열린 채로 다시 그린다.
            // 적어 둔 이름과 주소는 그대로 두고, 비밀번호는 다시 싣지 않는다.
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return otpScreen(resolved, request, email, name, phoneNumber, e.getMessage());
        }

        return loggedIn(subject, request, httpRequest, response);
    }

    /**
     * 비밀번호를 정해 가입한다. 이메일이 곧 로그인 아이디다.
     *
     * <p>길이를 먼저 본다. 인증번호는 확인하는 순간 쓰인 것이 되므로, 비밀번호가 틀려서 되돌려 보낼
     * 일이면 번호를 쓰기 전에 걸러야 사람이 번호를 다시 받지 않아도 된다.
     */
    private AuthenticatedSubject registerWithPassword(Realm realm, String email, String name,
                                                      String phoneNumber, String code, String password) {
        if (!realm.allowsSelfRegistrationWith(RegistrationMethod.PASSWORD)) {
            throw new IllegalArgumentException("이 영역은 비밀번호 가입을 지원하지 않습니다.");
        }
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new IllegalArgumentException(
                    "비밀번호는 " + PASSWORD_MIN + "자 이상 " + PASSWORD_MAX + "자 이하로 정하세요.");
        }
        return registerWithPassword.register(new RegisterWithPasswordCommand(
                realm, email.trim(), name.trim(), blankToNull(phoneNumber), email.trim(), password, code));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    /** 가입이 끝났다. 로그인 폼과 마지막 두 걸음이 같다. 세션을 심고 code 를 앱에 돌려준다. */
    private ModelAndView loggedIn(AuthenticatedSubject subject, AuthorizationRequest request,
                                  HttpServletRequest httpRequest, HttpServletResponse response) {
        loginSessionStarter.start(subject, httpRequest, response);
        return authorizationCodeRedirect.issueAndRedirect(request, subject);
    }

    private Realm requireAnyRegistration(String realm) {
        Realm resolved = authenticationRealm.of(realm);
        if (!resolved.allowsSelfRegistration()) {
            throw new NotFoundException("이 realm 은 셀프 가입을 지원하지 않습니다: " + realm);
        }
        return resolved;
    }

    private static void requireProfile(String email, String name) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("이메일을 확인하세요.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 적어 주세요.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 화면이 어떤 폼을 보여줄지는 realm 이 여는 방식이 정한다. */
    static ModelAndView registerScreen(Realm realm, AuthorizationRequest request) {
        return new ModelAndView("oauth/register")
                .addObject("realm", realm.name().toLowerCase())
                .addObject("request", request)
                .addObject("passwordAllowed", realm.allowsSelfRegistrationWith(RegistrationMethod.PASSWORD));
    }

    private static ModelAndView otpScreen(Realm realm, AuthorizationRequest request, String email,
                                          String name, String phoneNumber, String error) {
        return registerScreen(realm, request)
                .addObject("otpSent", true)
                .addObject("email", email)
                .addObject("name", name)
                .addObject("phoneNumber", phoneNumber)
                .addObject("error", error);
    }

    private static ModelAndView errorScreen(String reason) {
        return new ModelAndView("oauth/error").addObject("reason", reason);
    }
}
