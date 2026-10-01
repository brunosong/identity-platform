package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.ValidateRedirectUriUseCase;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.domain.shared.RegistrationMethod;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
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
 * 가입만 하는 입구 - {@code /realms/{realm}/register}.
 *
 * <p>가입 입구가 둘이다.
 * <pre>
 * /realms/{realm}/auth?prompt=create   앱의 인가 요청. 가입하면 로그인까지 되어 code 를 들고 앱으로 간다
 * /realms/{realm}/register             가입만. "가입을 완료했습니다" 를 보여주고 끝난다
 * </pre>
 * 이쪽은 로그인 세션을 심지 않고 code 도 내주지 않는다. 사람은 앱으로 돌아가 로그인을 다시 한다.
 * 가운데의 두 걸음(번호 받기, 번호와 비밀번호)은 같다({@link RegistrationSteps}).
 *
 * <p>인가 요청을 들고 오지 않으므로 {@code client_id} 와 {@code redirect_uri} 만 받는다. 둘 다 돌아갈 곳을
 * 보여주는 데만 쓴다. 그 앱에 등록된 주소가 아니면 링크를 보이지 않을 뿐 가입은 막지 않는다. 로그아웃의
 * {@code post_logout_redirect_uri} 와 같은 판단이다.
 */
@Controller
@RequiredArgsConstructor
public class SignUpController {

    private final AuthenticationRealm authenticationRealm;
    private final ValidateRedirectUriUseCase validateRedirectUri;
    private final RegistrationSteps registrationSteps;

    @GetMapping("/realms/{realm}/register")
    public ModelAndView screen(@PathVariable String realm,
                               @RequestParam(name = "client_id", required = false) String clientId,
                               @RequestParam(name = "redirect_uri", required = false) String redirectUri) {
        Realm resolved = authenticationRealm.requireSelfRegistration(realm, RegistrationMethod.EMAIL_OTP);
        return RegistrationScreen.standalone(resolved, clientId, redirectUri);
    }

    /** 1단계. 가입용 인증번호를 보낸다. 보냈는지 아닌지는 화면에서 가르지 않는다. */
    @PostMapping("/realms/{realm}/register/send-code")
    public ModelAndView sendCode(@PathVariable String realm,
                                 @RequestParam(name = "client_id", required = false) String clientId,
                                 @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String phoneNumber,
                                 HttpServletResponse response) {
        Realm resolved = authenticationRealm.requireSelfRegistration(realm, RegistrationMethod.EMAIL_OTP);

        try {
            registrationSteps.sendCode(resolved, email, name);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return RegistrationScreen.refilled(RegistrationScreen.standalone(resolved, clientId, redirectUri),
                    email, name, phoneNumber, e.getMessage());
        }
        return RegistrationScreen.codeSent(RegistrationScreen.standalone(resolved, clientId, redirectUri),
                email.trim(), name.trim(), phoneNumber, null);
    }

    /** 2단계. 가입하고 완료 화면을 보여준다. 로그인은 시키지 않는다. */
    @PostMapping("/realms/{realm}/register")
    public ModelAndView register(@PathVariable String realm,
                                 @RequestParam(name = "client_id", required = false) String clientId,
                                 @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String phoneNumber,
                                 @RequestParam(required = false) String code,
                                 @RequestParam(required = false) String password,
                                 HttpServletResponse response) {
        Realm resolved = authenticationRealm.requireSelfRegistration(realm, RegistrationMethod.EMAIL_OTP);

        AuthenticatedSubject registered;
        try {
            registered = registrationSteps.register(resolved, email, name, phoneNumber, code, password);
        } catch (IllegalArgumentException | AuthenticationFailedException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return RegistrationScreen.codeSent(RegistrationScreen.standalone(resolved, clientId, redirectUri),
                    email, name, phoneNumber, e.getMessage());
        }

        return new ModelAndView("oauth/registered")
                .addObject("email", email.trim())
                .addObject("returnUri", returnUriOf(resolved, clientId, redirectUri));
    }

    /** 그 앱에 등록된 주소일 때만 돌려준다. 아니면 링크를 보이지 않는다. */
    private String returnUriOf(Realm realm, String clientId, String redirectUri) {
        return validateRedirectUri.isRegistered(realm, clientId, redirectUri) ? redirectUri : null;
    }
}
