package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.FindLoginSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.LoginSessionCookie;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
 * <h2>{@code prompt=none} - 조용히 시도해 보는 길</h2>
 * 앱은 세션 쿠키를 읽지 못한다. 다른 출처의 쿠키이기 때문이다. 그래서 "이 브라우저가 이미
 * 로그인해 있는가" 를 알아낼 방법이 <b>여기로 와 보는 것</b> 하나뿐이다.
 *
 * <p>그런데 기본 동작으로는 조용히 와 볼 수가 없다. 세션이 없으면 로그인 화면이 떠버려서,
 * 구경만 하러 온 사람이 로그인 화면에 갇힌다. {@code prompt=none} 은 그 자리에서 화면 대신
 * {@code error=login_required} 를 달아 앱으로 돌려보낸다. <b>실패도 리다이렉트라서 앱이 코드로
 * 분기할 수 있다</b> - 그것이 이 파라미터가 하는 일의 전부다.
 *
 * <p>세션이 있을 때는 아무것도 달라지지 않는다. prompt 가 정하는 것은 <b>없을 때 무엇을 할지</b>
 * 하나다.
 *
 * <p>없는 파라미터도 여기서는 오류 화면이다. 400 본문 대신 사람이 읽을 화면을 준다 - 이 경로에
 * 도착하는 것은 API 호출이 아니라 브라우저다. {@code prompt} 가 이상한 값일 때도 같다.
 * 그 단계에서는 돌아갈 주소를 아직 믿을 수 없어 앱으로 보낼 수 없다.
 */
@Controller
@RequiredArgsConstructor
public class AuthorizationEndpointController {

    /** 화면을 띄우지 말라는 요청(OIDC Core 3.1.2.1). */
    private static final String SILENT = "none";

    /**
     * 로그인 대신 가입 화면을 띄우라는 요청(OIDC Prompt Create 1.0). 앱의 "회원가입" 버튼이 쓴다.
     * 가입 화면을 앱이 따로 두지 않고, 가입이 끝나면 로그인과 같은 끝(code)으로 돌아간다.
     */
    private static final String CREATE = "create";

    private final AuthenticationRealm authenticationRealm;
    private final StartAuthorizationUseCase startAuthorization;
    private final FindLoginSessionUseCase findLoginSession;
    private final AuthorizationCodeRedirect authorizationCodeRedirect;

    @GetMapping("/realms/{realm}/auth")
    public ModelAndView authorize(@PathVariable String realm,
                                  @RequestParam(name = "response_type", required = false) String responseType,
                                  AuthorizationParams params,
                                  @RequestParam(required = false) String prompt,
                                  @CookieValue(name = LoginSessionCookie.NAME, required = false) String sessionId) {
        // 받아줄 수 없는 요청은 여기서 예외로 끝나고 AuthorizationErrorScreen 이 화면을 그린다.
        // 모르는 realm 이면 404, 앱이나 주소가 틀렸으면 400 이다.
        requireSupportedPrompt(prompt);
        Realm resolved = authenticationRealm.of(realm);
        AuthorizationRequest request = startAuthorization.start(params.toCommand(resolved, responseType));

        // 가입해 달라는 요청이면 세션과 상관없이 가입 화면이다. 로그인해 있는 사람이 새 계정을
        // 만들려는 것일 수 있다. 요청 검증은 이미 끝났다.
        if (CREATE.equals(prompt)) {
            if (!resolved.allowsSelfRegistration()) {
                return AuthorizationErrorScreen.of("이 realm 은 셀프 가입을 지원하지 않습니다.", HttpStatus.BAD_REQUEST);
            }
            return RegistrationScreen.withinAuthorization(resolved, request);
        }

        // 이미 로그인해 있으면 묻지 않는다. 세션 확인을 요청 검증 뒤에 두는 것이 중요하다 -
        // 등록되지 않은 앱의 요청에 코드를 내주는 일이 없어야 한다.
        //
        // 세션이 없을 때 무엇을 할지만 prompt 가 정한다. 있을 때는 어느 쪽이든 코드를 내준다.
        return findLoginSession.findActive(resolved, sessionId)
                .map(subject -> authorizationCodeRedirect.issueAndRedirect(request, subject))
                .orElseGet(() -> SILENT.equals(prompt)
                        ? authorizationCodeRedirect.loginRequired(request)
                        : loginScreen(resolved, request));
    }

    /**
     * 아는 값은 {@code none} 과 {@code create} 다. 나머지는 받지 않는다.
     *
     * <p>모르는 값을 조용히 무시하면 {@code prompt=login} 을 보낸 쪽이 다시 물었다고 믿는데 실제로는
     * 세션이 그대로 통과한다. 재인증을 요구한 자리에서 그러면 곤란하다. 발급자 문서가 적어둔 값
     * ({@code prompt_values_supported})과도 어긋난다.
     */
    private static void requireSupportedPrompt(String prompt) {
        if (prompt != null && !prompt.isBlank() && !SILENT.equals(prompt) && !CREATE.equals(prompt)) {
            throw new IllegalArgumentException("지원하지 않는 prompt 입니다: " + prompt);
        }
    }

    private static ModelAndView loginScreen(Realm realm, AuthorizationRequest request) {
        return new ModelAndView("oauth/login")
                .addObject("realm", realm.name().toLowerCase())
                .addObject("registrationOpen", realm.allowsSelfRegistration())
                .addObject("request", request);
    }
}
