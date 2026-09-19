package com.brunosong.identityplatform.auth.service.web.broker;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithSocialUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialAuthorizationPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialIdentityVerifierPort;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.IssuedTokens;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import com.brunosong.identityplatform.auth.service.web.support.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 구글 브로커 - 브라우저를 구글로 보내고, 구글이 돌려보낸 것을 받는다.
 *
 * <h2>왜 여기가 받나</h2>
 * 구글이 브라우저를 돌려보낼 주소를 프론트엔드가 아니라 auth 로 잡았다. 그래서 구글 콘솔에
 * 등록할 주소가 이 하나뿐이고, 앱이 몇 개가 되든 구글 설정은 그대로다. 프론트엔드는 구글이라는
 * 것이 있는지도 모른다. Keycloak 이 {@code /realms/{realm}/broker/{provider}/endpoint} 로 하는 일과 같다.
 *
 * <h2>이 경로는 API 가 아니다</h2>
 * 브라우저가 주소창을 들고 직접 오가는 자리라 {@code /api} 아래가 아니다. 응답도 JSON 이 아니라
 * 리다이렉트이거나 사람이 볼 화면이다.
 *
 * <h2>포털에서만 연다</h2>
 * 소셜 로그인은 처음 들어온 계정에 신원을 새로 만들어준다. 어드민에 열면 아무나 소셜 로그인만으로
 * 직원 신원을 만들 수 있다. 다른 realm 에서는 404 다.
 *
 * <h2>아직 미완성이다</h2>
 * {@code state} 와 {@code nonce} 를 싣지 않는다. 그 둘은 우리가 만든 값을 어딘가 보관해 두었다가
 * 돌아온 값과 대조해야 의미가 있는데, 그 보관 자리를 아직 정하지 않았다. 지금은 code 가 여기까지
 * 도착하는 것만 확인한다.
 */
@RestController
@RequestMapping("/realms/{realm}/broker/google")
@RequiredArgsConstructor
public class GoogleBrokerController {

    /**
     * 소셜이 꺼진 서버에서는 이 빈이 없다. 없으면 이 경로도 없는 것으로 다룬다 - 설정하지 않은
     * provider 의 경로가 500 을 뱉으며 존재를 드러낼 이유가 없다.
     */
    /**
     * 시작할 때 만든 state 를 담아 두는 쿠키.
     *
     * <p>경로를 이 브로커 아래로 좁힌다. 다른 요청에 딸려 나갈 이유가 없는 값이다.
     *
     * <p><b>SameSite 는 Lax 여야 한다.</b> 콜백은 구글(다른 사이트)이 브라우저를 밀어 보내는
     * 최상위 이동이라, Strict 로 두면 쿠키가 실리지 않아 늘 대조에 실패한다. Lax 는 이런
     * 최상위 GET 이동에는 쿠키를 보낸다.
     */
    private static final String STATE_COOKIE = "SOCIAL_STATE";

    /** 구글에 다녀오는 시간이면 충분하다. 길게 두면 훔쳐 쓸 창만 넓어진다. */
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<SocialAuthorizationPort> authorizationProvider;
    private final ObjectProvider<SocialIdentityVerifierPort> verifierProvider;
    private final AuthenticateWithSocialUseCase authenticateWithSocial;
    private final AuthenticationRealm authenticationRealm;

    /**
     * 브라우저를 구글 로그인 화면으로 보낸다. 여기서부터 사용자는 우리 서버를 떠난다.
     *
     * <p>떠나기 전에 난수 {@code state} 를 만들어 <b>쿠키에 담고 구글에도 실어 보낸다.</b> 돌아왔을 때
     * 그 둘이 같은지 보는 것이 이 값의 전부다 - 묻는 것은 "유효한 값이냐" 가 아니라
     * <b>"이 브라우저가 시작한 흐름이냐"</b> 다. 서버 어딘가에 발급 목록을 두고 대조하면 아무것도
     * 막지 못한다. 공격자가 시작한 흐름의 state 도 그 목록에 들어 있기 때문이다.
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login(@PathVariable String realm, HttpServletRequest request) {
        authenticationRealm.requireRealm(realm, Realm.PORTAL);

        String state = newState();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, stateCookie(state, request.isSecure()).toString())
                .location(authorization().authorizationUri(SocialProvider.GOOGLE, state))
                .build();
    }

    /**
     * 구글이 브라우저를 돌려보내는 자리.
     *
     * <p>주소창에 실려 온 {@code code} 를 구글에 직접 물어 신원으로 바꾼다. 그 왕복은 서버끼리라
     * 브라우저가 알지 못한다.
     *
     * <p>지금은 알아낸 신원을 화면에 찍는 것으로 끝난다. 원래는 여기서 우리 신원에 연결하고
     * 우리 토큰을 발급해 앱으로 돌려보내야 한다.
     */
    @GetMapping(value = "/endpoint", produces = MediaType.TEXT_PLAIN_VALUE)
    public String endpoint(@PathVariable String realm,
                           @RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @CookieValue(name = STATE_COOKIE, required = false) String startedState,
                           @RequestParam(required = false) String error,
                           HttpServletResponse response) {
        Realm resolved = authenticationRealm.requireRealm(realm, Realm.PORTAL);
        verifier();   // 소셜이 꺼져 있으면 이 경로도 없는 것으로 다룬다

        // 한 번 쓰면 버린다. 남겨두면 같은 값으로 두 번째 콜백을 받아줄 수 있게 된다.
        response.addHeader(HttpHeaders.SET_COOKIE, expiredStateCookie().toString());
        requireStartedHere(state, startedState);

        // 사용자가 동의 화면에서 취소하면 code 대신 error 가 온다. 정상적인 흐름이라 예외가 아니다.
        if (error != null) {
            return "구글이 거절했다: " + error;
        }

        AuthenticationResult result = authenticateWithSocial.authenticate(
                new SocialAuthCommand(resolved, SocialProvider.GOOGLE, code));
        IssuedTokens tokens = IssuedTokens.of(result.tokens());

        return """
                우리 신원으로 로그인됐다.

                subjectId = %s
                realm     = %s

                access  = %s
                refresh = %s
                """.formatted(result.subjectId(), result.realm(), tokens.accessToken(), tokens.refreshToken());
    }

    /**
     * 이 콜백이 이 브라우저가 시작한 흐름의 결과인지 확인한다.
     *
     * <p>막는 것은 로그인 CSRF 다. 공격자가 자기 구글 계정으로 시작해 받은 콜백 주소를 피해자에게
     * 열게 하면, 피해자 브라우저에 <b>공격자 계정</b>으로 로그인된 세션이 생긴다. 피해자는 자기
     * 계정인 줄 알고 결제수단이나 문서를 넣고, 나중에 공격자가 자기 계정에서 그것을 본다.
     *
     * <p>공격자가 만든 주소의 state 는 공격자 브라우저의 쿠키에 있던 값이라, 피해자 브라우저에는
     * 짝이 없다. 그래서 여기서 걸린다.
     */
    private static void requireStartedHere(String state, String startedState) {
        if (state == null || startedState == null || !startedState.equals(state)) {
            throw new UnauthorizedException("이 브라우저에서 시작한 로그인이 아닙니다.");
        }
    }

    private static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ResponseCookie stateCookie(String state, boolean secure) {
        return baseStateCookie(state, secure).maxAge(STATE_TTL).build();
    }

    private static ResponseCookie expiredStateCookie() {
        // 지울 때도 같은 속성이어야 브라우저가 같은 쿠키로 알아본다.
        return baseStateCookie("", false).maxAge(0).build();
    }

    private static ResponseCookie.ResponseCookieBuilder baseStateCookie(String value, boolean secure) {
        return ResponseCookie.from(STATE_COOKIE, value)
                // 스크립트가 읽을 이유가 없는 값이다.
                .httpOnly(true)
                // http 로 띄운 로컬에서 secure 를 켜면 브라우저가 쿠키를 아예 저장하지 않는다.
                // 요청이 https 로 들어왔을 때만 켠다.
                .secure(secure)
                .path("/realms")
                .sameSite("Lax");
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
