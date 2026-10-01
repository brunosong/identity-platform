package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.EndLoginSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.ValidateRedirectUriUseCase;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.LoginSessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 로그아웃 - {@code GET /realms/{realm}/logout}.
 *
 * <p>세션을 끊고 쿠키를 지운다. 한 번 로그인이 여러 앱에 걸렸던 것처럼 <b>한 번 로그아웃도
 * 여러 앱에 걸린다</b> - 다음 인가 요청부터는 어느 앱이든 로그인 화면이 다시 뜬다.
 *
 * <h2>이미 발급된 토큰은 죽지 않는다</h2>
 * access 토큰은 서명만 맞으면 통하는 무상태 값이다. 세션을 끊어도 남은 수명 동안 살아 있고,
 * 그 토큰을 쥔 앱은 계속 API 를 부를 수 있다. 여기서 끊는 것은 "다음에 또 로그인시켜 줄
 * 것인가" 하나다. 그래서 앱도 자기가 들고 있는 토큰을 같이 버려야 로그아웃이 완성된다.
 *
 * <h2>돌아갈 주소는 등록된 것만</h2>
 * {@code post_logout_redirect_uri} 도 요청자가 적어 보내는 값이라 그대로 믿지 않는다.
 * 그 앱에 등록된 주소가 아니면 돌려보내지 않고 우리 화면에서 끝낸다. 인가 요청에서 쓰는
 * 규칙과 같다. 다만 여기서는 거절이 흐름을 막지 않는다. 세션은 이미 끊겼다.
 *
 * <p>GET 이다. 브라우저 주소창이 통째로 넘어와야 쿠키가 실리고, 쿠키가 없으면 끊을 세션을
 * 찾지 못한다. 그래서 {@code fetch} 로 부르는 API 가 될 수 없다.
 */
@Controller
@RequiredArgsConstructor
public class LogoutEndpointController {

    private final AuthenticationRealm authenticationRealm;
    private final EndLoginSessionUseCase endLoginSession;
    private final ValidateRedirectUriUseCase validateRedirectUri;

    @GetMapping("/realms/{realm}/logout")
    public ModelAndView logout(@PathVariable String realm,
                               @RequestParam(name = "client_id", required = false) String clientId,
                               @RequestParam(name = "post_logout_redirect_uri", required = false) String redirectUri,
                               @RequestParam(required = false) String state,
                               @CookieValue(name = LoginSessionCookie.NAME, required = false) String sessionId,
                               HttpServletRequest httpRequest, HttpServletResponse response) {
        Realm resolved = authenticationRealm.of(realm);

        // 먼저 끊는다. 돌아갈 주소를 따지는 일과 상관없이 로그아웃은 이뤄져야 한다.
        endLoginSession.end(sessionId);
        response.addHeader(HttpHeaders.SET_COOKIE,
                LoginSessionCookie.expired(resolved, httpRequest.isSecure()).toString());

        if (redirectUri == null) {
            return loggedOutScreen(null);
        }
        if (!validateRedirectUri.isRegistered(resolved, clientId, redirectUri)) {
            return loggedOutScreen("등록되지 않은 주소라 돌려보내지 않았습니다: " + redirectUri);
        }

        return new ModelAndView(redirectTo(redirectUri, state));
    }

    private static RedirectView redirectTo(String redirectUri, String state) {
        UriComponentsBuilder location = UriComponentsBuilder.fromUriString(redirectUri);
        if (state != null) {
            location.queryParam("state", state);
        }

        RedirectView redirect = new RedirectView(location.encode().toUriString());
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        return redirect;
    }

    private static ModelAndView loggedOutScreen(String notice) {
        return new ModelAndView("oauth/logout").addObject("notice", notice);
    }
}
