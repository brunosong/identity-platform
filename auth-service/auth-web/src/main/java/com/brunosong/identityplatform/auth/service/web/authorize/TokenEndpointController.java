package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.token.TokenProperties;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidGrantException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.ExchangeAuthorizationCodeUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.ExchangeAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import com.brunosong.identityplatform.auth.service.web.support.RefreshTokenCookie;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 토큰 교환 - {@code POST /realms/{realm}/token}.
 *
 * <p>로그인 흐름의 마지막 걸음이고, 이 흐름에서 <b>유일하게 사람이 아니라 앱이 부르는</b> 자리다.
 * 브라우저 화면이 오가지 않으므로 주고받는 것이 HTML 이 아니라 폼 값과 JSON 이다.
 *
 * <h2>모양을 우리가 정하지 않는다</h2>
 * 요청은 폼 인코딩, 응답은 {@code snake_case} JSON 이다. 우리 다른 API 는 {@code camelCase} 인데
 * 여기만 다른 이유는, 이 자리는 표준 OAuth 라이브러리가 그대로 읽을 수 있어야 하기 때문이다
 * (RFC 6749 4.1.4, 5.1). 오류 이름도 마찬가지다 - 우리가 지은 단어를 쓰면 그쪽은 못 알아듣는다.
 *
 * <p>폼 인코딩에는 덤이 하나 있다. 브라우저가 {@code application/x-www-form-urlencoded} 를
 * 단순 요청으로 쳐서 preflight(OPTIONS)가 나가지 않는다. JSON 으로 받으면 요청마다 왕복이 하나 는다.
 *
 * <h2>만료(expires_in)를 싣지 않는다</h2>
 * access 토큰의 {@code exp} 클레임에 이미 있고, 두 군데에 두면 언젠가 어긋난다. 이 저장소가
 * 로그인 응답에서 이미 고른 답이다({@code IssuedTokens}). 명세에서도 권고일 뿐 필수는 아니다.
 *
 * <h2>실패는 사유를 나누지 않는다</h2>
 * 없는 코드든 만료든 남의 코드든 {@code invalid_grant} 하나다. 무엇이 틀렸는지는 로그에만 남긴다 -
 * 응답에 적으면 코드를 주운 쪽이 그것으로 좁혀갈 수 있고, 정상적인 앱은 어차피 로그인을 다시
 * 시작하는 것 말고 할 일이 없다.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TokenEndpointController {

    private static final String AUTHORIZATION_CODE = "authorization_code";

    private final AuthenticationRealm authenticationRealm;
    private final ExchangeAuthorizationCodeUseCase exchangeAuthorizationCode;
    private final TokenProperties tokenProperties;

    @PostMapping(path = "/realms/{realm}/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public TokenResponse token(@PathVariable String realm,
                               @RequestParam(name = "grant_type", required = false) String grantType,
                               @RequestParam(required = false) String code,
                               @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                               @RequestParam(name = "client_id", required = false) String clientId,
                               @RequestParam(name = "code_verifier", required = false) String codeVerifier,
                               HttpServletRequest httpRequest, HttpServletResponse response) {
        if (!AUTHORIZATION_CODE.equals(grantType)) {
            throw new UnsupportedGrantTypeException(grantType);
        }

        Realm resolved = authenticationRealm.of(realm);
        AuthenticationResult result = exchangeAuthorizationCode.exchange(
                new ExchangeAuthorizationCodeCommand(resolved, code, clientId,
                        redirectUri, codeVerifier));

        // refresh 토큰은 본문이 아니라 쿠키로 나간다. 아래 응답에도 싣지 않는다.
        response.addHeader(HttpHeaders.SET_COOKIE,
                RefreshTokenCookie.of(result.tokens().refreshToken(), resolved,
                        httpRequest.isSecure(),
                        Duration.ofMillis(tokenProperties.getRefreshExpiration())).toString());

        return TokenResponse.of(result);
    }

    /** 코드를 토큰으로 바꿀 수 없다. 사유는 로그에만 남는다. */
    @ExceptionHandler({InvalidGrantException.class, AuthenticationFailedException.class})
    ResponseEntity<TokenError> invalidGrant(RuntimeException e) {
        log.warn("토큰 교환 실패: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new TokenError("invalid_grant"));
    }

    /** 이 엔드포인트가 아는 방식이 아니다. */
    @ExceptionHandler(UnsupportedGrantTypeException.class)
    ResponseEntity<TokenError> unsupportedGrantType(UnsupportedGrantTypeException e) {
        log.warn("지원하지 않는 grant_type: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new TokenError("unsupported_grant_type"));
    }

    /** 그런 realm 이 없다. 화면이 아니라 기계가 읽는 자리라 오류도 명세의 모양으로 낸다. */
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<TokenError> unknownRealm(NotFoundException e) {
        log.warn("토큰 교환 실패: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new TokenError("invalid_request"));
    }

    static class UnsupportedGrantTypeException extends RuntimeException {
        UnsupportedGrantTypeException(String grantType) {
            super(String.valueOf(grantType));
        }
    }

    /**
     * <b>{@code refresh_token} 이 없다.</b> 그것은 {@code Set-Cookie} 로 나간다.
     *
     * <p>본문에도 함께 실으면 쿠키로 옮긴 의미가 없다. 스크립트가 그 값을 읽어 어딘가에 보관하는
     * 순간 {@code HttpOnly} 는 장식이 된다. 명세에서도 {@code refresh_token} 은 선택 항목이다
     * (RFC 6749 5.1).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("token_type") String tokenType) {

        static TokenResponse of(AuthenticationResult result) {
            return new TokenResponse(result.tokens().accessToken(), "Bearer");
        }
    }

    public record TokenError(String error) {
    }
}
