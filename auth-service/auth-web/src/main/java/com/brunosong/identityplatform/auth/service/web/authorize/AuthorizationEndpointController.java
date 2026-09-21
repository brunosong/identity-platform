package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidAuthorizationRequestException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.AuthorizationRequestCommand;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 인가 요청의 입구 - {@code GET /realms/{realm}/auth}.
 *
 * <p>앱은 브라우저를 여기로 보내고 빠진다. 여기서부터 로그인이 끝날 때까지 앱은 아무것도 보지
 * 못한다. 비밀번호가 앱을 거치지 않는다는 것이 이 구조의 요점이고, 그래서 이 화면은 우리가 그린다.
 *
 * <p>하는 일은 둘이다. 받아들일 요청인지 확인하고, 로그인 화면을 그린다. 확인을 통과하지 못하면
 * <b>돌려보내지 않고</b> 우리 화면에서 끝낸다 - 그 주소는 아직 믿을 수 없는 값이다.
 *
 * <p>없는 파라미터도 여기서는 오류 화면이다. 400 본문 대신 사람이 읽을 화면을 준다 - 이 경로에
 * 도착하는 것은 API 호출이 아니라 브라우저다.
 */
@Controller
@RequiredArgsConstructor
public class AuthorizationEndpointController {

    private final AuthenticationRealm authenticationRealm;
    private final StartAuthorizationUseCase startAuthorization;

    @GetMapping("/realms/{realm}/auth")
    public String authorize(@PathVariable String realm,
                            @RequestParam(name = "response_type", required = false) String responseType,
                            @RequestParam(name = "client_id", required = false) String clientId,
                            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                            @RequestParam(required = false) String scope,
                            @RequestParam(required = false) String state,
                            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                            @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
                            @RequestParam(required = false) String nonce,
                            Model model, HttpServletResponse response) {
        try {
            AuthorizationRequest request = startAuthorization.start(new AuthorizationRequestCommand(
                    authenticationRealm.of(realm), responseType, clientId, redirectUri, scope, state,
                    codeChallenge, codeChallengeMethod, nonce));

            model.addAttribute("realm", realm.toLowerCase());
            model.addAttribute("request", request);
            return "oauth/login";
        } catch (NotFoundException e) {
            // 그런 realm 이 없다. 화면은 같고 상태만 다르다 - 사람은 사유를 읽고, 기계는 코드를 읽는다.
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("reason", e.getMessage());
            return "oauth/error";
        } catch (InvalidAuthorizationRequestException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("reason", e.getMessage());
            return "oauth/error";
        }
    }
}
