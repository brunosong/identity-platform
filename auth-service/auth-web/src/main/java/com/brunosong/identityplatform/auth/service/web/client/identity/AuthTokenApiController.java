package com.brunosong.identityplatform.auth.service.web.client.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RefreshTokenUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.token.TokenProperties;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.IssuedTokens;
import com.brunosong.identityplatform.auth.service.web.support.RefreshTokenCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 토큰 재발급 API. refresh 토큰을 받아 새 access/refresh 를 발급한다.
 * 재발급 시점의 권한과 리비전이 다시 실린다.
 *
 * <h2>refresh 토큰을 두 군데서 받는다</h2>
 * <b>쿠키가 먼저다.</b> 인가 코드 흐름으로 로그인하면 refresh 토큰이 {@code HttpOnly} 쿠키로
 * 나가고({@link RefreshTokenCookie}) 호출자는 그 값을 알지 못한다. 브라우저가 알아서 싣는다.
 *
 * <p>본문도 아직 받는다. 비밀번호·이메일 인증번호·소셜 로그인은 여전히 refresh 토큰을 응답
 * 본문으로 내리고, 그쪽으로 들어온 호출자는 손에 든 값을 낼 수밖에 없다. <b>한 흐름만 쿠키로
 * 옮긴 탓에 생긴 과도기다</b> - 나머지도 옮기면 본문 갈래는 지운다.
 *
 * <p>재발급하면 refresh 토큰도 새로 나온다. 쿠키로 들어왔으면 쿠키를 다시 굽는다. 안 그러면
 * 브라우저가 이미 지나간 값을 계속 들고 다닌다.
 *
 * <p>realm 은 경로가 정한다. refresh 토큰에는 subjectId 만으로 주체를 특정할 수 없고(유일키는
 * 유형+식별자다), 검증도 그 realm 의 공개키로 한다. 다른 realm 을 지목하면 서명 단계에서 걸린다.
 */
@RestController
@RequestMapping("/api/auth/realms/{realm}/token")
@RequiredArgsConstructor
public class AuthTokenApiController {

    private final RefreshTokenUseCase refreshToken;
    private final AuthenticationRealm authenticationRealm;
    private final TokenProperties tokenProperties;

    @PostMapping("/refresh")
    public RefreshResponse refresh(@PathVariable String realm,
                                   @CookieValue(name = RefreshTokenCookie.NAME, required = false)
                                   String cookieToken,
                                   @RequestBody(required = false) RefreshRequest request,
                                   HttpServletRequest httpRequest, HttpServletResponse response) {
        Realm resolved = authenticationRealm.of(realm);
        String presented = StringUtils.hasText(cookieToken) ? cookieToken : bodyToken(request);

        // 재발급될 토큰의 aud 는 경로의 realm 이 정한다. 이 토큰을 쥔 쪽이 향할 곳을 고를 수 없다.
        AuthenticationResult result = refreshToken.refresh(resolved, presented);

        // 쿠키로 들어온 호출만 쿠키를 갱신하고, 그 경우 본문에서는 뺀다. 본문으로 들어온 쪽에
        // 쿠키를 심으면 그쪽 앱이 들고 있는 값과 브라우저의 값이 갈라진다.
        if (StringUtils.hasText(cookieToken)) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    RefreshTokenCookie.of(result.tokens().refreshToken(), resolved,
                            httpRequest.isSecure(),
                            Duration.ofMillis(tokenProperties.getRefreshExpiration())).toString());
            return new RefreshResponse(result.subjectId(), result.realm().name(),
                    IssuedTokens.withoutRefresh(result.tokens()));
        }

        return new RefreshResponse(result.subjectId(), result.realm().name(),
                IssuedTokens.of(result.tokens()));
    }

    /**
     * 쿠키도 본문도 없으면 낼 것이 없다는 뜻이다.
     *
     * <p>사유를 나누지 않고 인증 실패로 낸다. "쿠키가 없다" 와 "토큰이 틀렸다" 를 가르면 호출한
     * 쪽이 그것으로 상태를 좁혀갈 수 있다.
     */
    private static String bodyToken(RefreshRequest request) {
        if (request == null || !StringUtils.hasText(request.refreshToken())) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
        return request.refreshToken();
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record RefreshResponse(String subjectId, String realm, IssuedTokens tokens) {
    }
}
