package com.brunosong.identityplatform.auth.service.web.broker;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.EstablishSocialAuthenticationUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialAuthorizationPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialIdentityVerifierPort;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.authorize.LoginSessionStarter;
import com.brunosong.identityplatform.auth.service.web.authorize.AuthorizationErrorScreen;
import com.brunosong.identityplatform.auth.service.web.authorize.AuthorizationParams;
import com.brunosong.identityplatform.auth.service.web.broker.BrokerCookies.Trip;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 구글 브로커 - 로그인 화면의 구글 버튼이 브라우저를 여기로 보내고, 구글이 여기로 돌려보낸다.
 *
 * <h2>왜 여기가 받나</h2>
 * 구글이 브라우저를 돌려보낼 주소를 앱이 아니라 auth 로 잡았다. 그래서 구글 콘솔에 등록할 주소가
 * 이 하나뿐이고, 앱이 몇 개가 되든 구글 설정은 그대로다. 앱은 구글이라는 것이 있는지도 모른다.
 * Keycloak 이 {@code /realms/{realm}/broker/{provider}/endpoint} 로 하는 일과 같다.
 *
 * <h2>구글에 다녀온 뒤 원래 인가 요청으로 되돌아간다</h2>
 * <pre>
 * 로그인 화면 [구글]  → /broker/google/login?인가 요청   인가 요청을 쿠키에 담고 구글로
 * 구글                → /broker/google/endpoint?code    신원 확인, 로그인 세션 시작
 *                     → /realms/{realm}/auth?인가 요청    세션이 있으니 code 를 내준다
 * </pre>
 * 마지막 걸음이 요점이다. 여기서 code 를 직접 내주지 않고 인가 요청의 입구로 되돌린다. 세션이
 * 생겼으니 그 입구가 "이미 로그인한 브라우저" 로 보고 code 를 내준다. code 를 내주는 길이
 * 하나로 남아서, 요청 검증과 발급 규칙이 구글 경로에서 따로 갈라지지 않는다.
 *
 * <h2>포털에서만 연다</h2>
 * 소셜 로그인은 처음 들어온 계정에 신원을 새로 만들어준다. 어드민에 열면 아무나 소셜 로그인만으로
 * 직원 신원을 만들 수 있다. 다른 realm 에서는 404 다.
 *
 * <h2>구글에 nonce 는 싣지 않는다</h2>
 * 구글의 id_token 은 우리 서버가 TLS 로 구글 토큰 엔드포인트에 직접 물어 받는다. 중간에 끼어들
 * 자리가 없어 명세상 선택이다(OIDC Core 3.1.3.7).
 */
@Controller
@RequestMapping("/realms/{realm}/broker/google")
@RequiredArgsConstructor
@Slf4j
public class GoogleBrokerController {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 소셜이 꺼진 서버에서는 이 빈이 없다. 없으면 이 경로도 없는 것으로 다룬다 - 설정하지 않은
     * provider 의 경로가 500 을 뱉으며 존재를 드러낼 이유가 없다.
     */
    private final ObjectProvider<SocialAuthorizationPort> authorizationProvider;
    private final ObjectProvider<SocialIdentityVerifierPort> verifierProvider;
    private final EstablishSocialAuthenticationUseCase establishSocialAuthentication;
    private final StartAuthorizationUseCase startAuthorization;
    private final LoginSessionStarter loginSessionStarter;
    private final AuthenticationRealm authenticationRealm;
    private final BrokerCookies brokerCookies;

    /**
     * 브라우저를 구글 로그인 화면으로 보낸다. 로그인 화면이 들고 있던 인가 요청을 그대로 받는다.
     *
     * <p>인가 요청을 여기서 한 번 검증한다. 등록되지 않은 앱의 요청이면 구글에 보내지 않는다.
     * 돌아온 뒤 입구에서 다시 검증하지만, 받아줄 수 없는 요청으로 사람을 구글까지 다녀오게 할
     * 이유가 없다.
     *
     * <p>떠나기 전에 난수 {@code state} 를 만들어 <b>쿠키에 담고 구글에도 실어 보낸다.</b> 돌아왔을 때
     * 그 둘이 같은지 보는 것이 이 값의 전부다. 묻는 것은 "유효한 값이냐" 가 아니라
     * <b>"이 브라우저가 시작한 흐름이냐"</b> 다.
     */
    @GetMapping("/login")
    public ModelAndView login(@PathVariable String realm,
                              AuthorizationParams params,
                              HttpServletRequest httpRequest,
                              HttpServletResponse response) {
        Realm resolved = authenticationRealm.requireRealm(realm, Realm.PORTAL);
        SocialAuthorizationPort authorization = authorization();

        AuthorizationRequest request = startAuthorization.start(params.toCommand(resolved));

        String socialState = newState();
        brokerCookies.put(httpRequest, response, new Trip(socialState, resumeQuery(request)));
        return redirect(authorization.authorizationUri(SocialProvider.GOOGLE, socialState).toString());
    }

    /**
     * 구글이 브라우저를 돌려보내는 자리.
     *
     * <p>주소창에 실려 온 {@code code} 를 구글에 직접 물어 신원으로 바꾼다. 그 왕복은 서버끼리라
     * 브라우저가 알지 못한다. 신원이 확인되면 로그인 세션을 심고 원래 인가 요청으로 되돌린다.
     *
     * <p>사람이 구글 동의 화면에서 취소하면 code 대신 {@code error} 가 온다. 그때는 세션 없이
     * 되돌린다. 입구가 로그인 화면을 다시 그리니, 다른 방법으로 로그인하면 된다.
     */
    @GetMapping("/endpoint")
    public ModelAndView endpoint(@PathVariable String realm,
                                 @RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(required = false) String error,
                                 HttpServletRequest httpRequest,
                                 HttpServletResponse response) {
        Realm resolved = authenticationRealm.requireRealm(realm, Realm.PORTAL);
        verifier();   // 소셜이 꺼져 있으면 이 경로도 없는 것으로 다룬다

        // 한 번 쓰면 버린다. 꺼내면서 지운다.
        Optional<Trip> trip = brokerCookies.take(httpRequest, response);
        if (trip.isEmpty() || !startedHere(state, trip.get().state())) {
            return AuthorizationErrorScreen.of("이 브라우저에서 시작한 로그인이 아닙니다. 앱에서 로그인을 다시 시작하세요.",
                    HttpStatus.BAD_REQUEST);
        }
        String authorizeUrl = authorizeUrl(realm, trip.get().resumeQuery());

        if (error != null) {
            log.info("구글이 로그인을 거절했다: {}", error);
            return redirect(authorizeUrl);
        }

        AuthenticatedSubject subject;
        try {
            subject = establishSocialAuthentication.withSocial(
                    new SocialAuthCommand(resolved, SocialProvider.GOOGLE, code));
        } catch (AuthenticationFailedException e) {
            return AuthorizationErrorScreen.of(e.getMessage(), HttpStatus.UNAUTHORIZED);
        }

        loginSessionStarter.start(subject, httpRequest, response);
        return redirect(authorizeUrl);
    }

    /**
     * 이 콜백이 이 브라우저가 시작한 흐름의 결과인지 확인한다.
     *
     * <p>막는 것은 로그인 CSRF 다. 공격자가 자기 구글 계정으로 시작해 받은 콜백 주소를 피해자에게
     * 열게 하면, 피해자 브라우저에 <b>공격자 계정</b>으로 로그인된 세션이 생긴다. 공격자가 만든
     * 주소의 state 는 공격자 브라우저의 쿠키에 있던 값이라, 피해자 브라우저에는 짝이 없다.
     */
    private static boolean startedHere(String state, String startedState) {
        return state != null && startedState != null && startedState.equals(state);
    }

    /** 검증을 통과한 값으로 질의를 다시 만든다. 로그인 화면이 보낸 글자를 그대로 옮기지 않는다. */
    private static String resumeQuery(AuthorizationRequest request) {
        UriComponentsBuilder query = UriComponentsBuilder.newInstance()
                .queryParam("response_type", "code")
                .queryParam("client_id", request.getClientId())
                .queryParam("redirect_uri", request.getRedirectUri())
                .queryParam("code_challenge", request.getCodeChallenge())
                .queryParam("code_challenge_method", "S256");
        if (request.getScope() != null) query.queryParam("scope", request.getScope());
        if (request.getState() != null) query.queryParam("state", request.getState());
        if (request.getNonce() != null) query.queryParam("nonce", request.getNonce());
        return query.encode().build().getQuery();
    }

    private static String authorizeUrl(String realm, String query) {
        return "/realms/" + realm.toLowerCase() + "/auth?" + query;
    }

    private static ModelAndView redirect(String url) {
        return new ModelAndView(new RedirectView(url));
    }

    private static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private SocialAuthorizationPort authorization() {
        SocialAuthorizationPort port = authorizationProvider.getIfAvailable();
        if (port == null) {
            throw new NotFoundException("이 서버에는 소셜 로그인이 설정돼 있지 않습니다.");
        }
        return port;
    }

    private SocialIdentityVerifierPort verifier() {
        SocialIdentityVerifierPort port = verifierProvider.getIfAvailable();
        if (port == null) {
            throw new NotFoundException("이 서버에는 소셜 로그인이 설정돼 있지 않습니다.");
        }
        return port;
    }
}
