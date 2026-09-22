package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.FindLoginSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidAuthorizationRequestException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.AuthorizationRequestCommand;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.LoginSessionCookie;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * 인가 요청의 입구 - {@code GET /realms/{realm}/auth}.
 *
 * <p>앱은 브라우저를 여기로 보내고 빠진다. 여기서부터 로그인이 끝날 때까지 앱은 아무것도 보지
 * 못한다. 비밀번호가 앱을 거치지 않는다는 것이 이 구조의 요점이고, 그래서 이 화면은 우리가 그린다.
 *
 * <p>하는 일은 셋이다. 받아들일 요청인지 확인하고, 이 브라우저가 이미 로그인해 있는지 보고,
 * 아니면 로그인 화면을 그린다. 확인을 통과하지 못하면 <b>돌려보내지 않고</b> 우리 화면에서
 * 끝낸다 - 그 주소는 아직 믿을 수 없는 값이다.
 *
 * <h2>이미 로그인해 있으면 화면이 뜨지 않는다</h2>
 * 쿠키의 세션이 살아 있으면 비밀번호를 다시 묻지 않고 그 자리에서 코드를 내준다. 사람 눈에는
 * 앱을 눌렀더니 그냥 로그인돼 있는 것으로 보인다. <b>통합 로그인이 성립하는 자리가 여기다.</b>
 *
 * <p>동의 화면은 없다. 우리 앱들만 등록되어 있어서 "이 앱에 정보를 주겠습니까" 를 물을 상대가
 * 아직 없다. 남의 앱이 등록되는 날 그 화면이 이 사이에 들어온다.
 *
 * <p>없는 파라미터도 여기서는 오류 화면이다. 400 본문 대신 사람이 읽을 화면을 준다 - 이 경로에
 * 도착하는 것은 API 호출이 아니라 브라우저다.
 */
@Controller
@RequiredArgsConstructor
public class AuthorizationEndpointController {

    private final AuthenticationRealm authenticationRealm;
    private final StartAuthorizationUseCase startAuthorization;
    private final FindLoginSessionUseCase findLoginSession;
    private final AuthorizationCodeRedirect authorizationCodeRedirect;

    @GetMapping("/realms/{realm}/auth")
    public ModelAndView authorize(@PathVariable String realm,
                            @RequestParam(name = "response_type", required = false) String responseType,
                            @RequestParam(name = "client_id", required = false) String clientId,
                            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                            @RequestParam(required = false) String scope,
                            @RequestParam(required = false) String state,
                            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                            @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
                            @RequestParam(required = false) String nonce,
                            @CookieValue(name = LoginSessionCookie.NAME, required = false) String sessionId,
                            HttpServletResponse response) {
        AuthorizationRequest request;
        Realm resolved;
        try {
            resolved = authenticationRealm.of(realm);
            request = startAuthorization.start(new AuthorizationRequestCommand(
                    resolved, responseType, clientId, redirectUri, scope, state,
                    codeChallenge, codeChallengeMethod, nonce));
        } catch (NotFoundException e) {
            // 그런 realm 이 없다. 화면은 같고 상태만 다르다 - 사람은 사유를 읽고, 기계는 코드를 읽는다.
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return errorScreen(e.getMessage());
        } catch (InvalidAuthorizationRequestException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return errorScreen(e.getMessage());
        }

        // 이미 로그인해 있으면 묻지 않는다. 세션 확인을 요청 검증 뒤에 두는 것이 중요하다 -
        // 등록되지 않은 앱의 요청에 코드를 내주는 일이 없어야 한다.
        return findLoginSession.findActive(resolved, sessionId)
                .map(subject -> authorizationCodeRedirect.issueAndRedirect(request, subject))
                .orElseGet(() -> loginScreen(realm, request));
    }

    private static ModelAndView loginScreen(String realm, AuthorizationRequest request) {
        return new ModelAndView("oauth/login")
                .addObject("realm", realm.toLowerCase())
                .addObject("request", request);
    }

    private static ModelAndView errorScreen(String reason) {
        return new ModelAndView("oauth/error").addObject("reason", reason);
    }
}
