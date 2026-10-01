package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.domain.shared.RegistrationMethod;
import org.springframework.web.servlet.ModelAndView;

/**
 * 가입 화면({@code oauth/register}). 입구 둘이 같은 화면을 쓴다.
 *
 * <p>입구마다 다른 것은 폼이 어디로 제출되나와 숨은 칸에 무엇을 들고 다니나뿐이다. 앱의 인가 요청으로 온
 * 가입은 그 요청을 통째로, 가입만 하는 입구는 돌아갈 앱과 주소만 든다. 나머지(걸음마다 무엇이 보이나,
 * 적어 둔 값을 다시 채우나)는 같아야 한다. 한쪽만 고치면 두 입구의 화면이 어긋난다.
 */
final class RegistrationScreen {

    private RegistrationScreen() {
    }

    /** 앱의 인가 요청으로 온 가입. 끝나면 로그인을 확정하고 앱으로 돌아간다. */
    static ModelAndView withinAuthorization(Realm realm, AuthorizationRequest request) {
        return base(realm, "/auth/register").addObject("request", request);
    }

    /** 가입만 하는 입구. 끝나면 완료 화면이다. */
    static ModelAndView standalone(Realm realm, String clientId, String redirectUri) {
        return base(realm, "/register")
                .addObject("clientId", clientId)
                .addObject("redirectUri", redirectUri);
    }

    /** 1단계를 적어 둔 값과 사유와 함께 다시 그린다. */
    static ModelAndView refilled(ModelAndView screen, String email, String name, String phoneNumber, String error) {
        return screen.addObject("email", email)
                .addObject("name", name)
                .addObject("phoneNumber", phoneNumber)
                .addObject("error", error);
    }

    /** 인증번호 칸이 열린 2단계. 비밀번호는 다시 싣지 않는다. */
    static ModelAndView codeSent(ModelAndView screen, String email, String name, String phoneNumber, String error) {
        return refilled(screen, email, name, phoneNumber, error).addObject("otpSent", true);
    }

    /** 어떤 폼을 보여줄지는 realm 이 여는 방식이 정한다. */
    private static ModelAndView base(Realm realm, String path) {
        String lowered = realm.name().toLowerCase();
        return new ModelAndView("oauth/register")
                .addObject("realm", lowered)
                .addObject("formBase", "/realms/" + lowered + path)
                .addObject("passwordAllowed", realm.allowsSelfRegistrationWith(RegistrationMethod.PASSWORD));
    }
}
