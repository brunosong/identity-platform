package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RefreshTokenUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidClientException;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidGrantException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.ExchangeAuthorizationCodeUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.ExchangeAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토큰 엔드포인트 - {@code POST /realms/{realm}/token}.
 *
 * <p>로그인 흐름에서 <b>유일하게 사람이 아니라 앱이 부르는</b> 자리다. 브라우저 화면이 오가지
 * 않으므로 주고받는 것이 HTML 이 아니라 폼 값과 JSON 이다.
 *
 * <h2>하는 일이 둘인데 주소는 하나다</h2>
 * <pre>
 * grant_type=authorization_code   코드를 토큰으로 바꾼다
 * grant_type=refresh_token        refresh 토큰으로 새 토큰을 받는다
 * </pre>
 *
 * 명세가 그렇게 정했다(RFC 6749 4.1.3, 6). 무엇을 하는 요청인지는 주소가 아니라 {@code grant_type}
 * 이 가른다. 한때 재발급만 {@code /api/auth/realms/{realm}/token/refresh} 라는 우리 주소에 있었는데,
 * 표준 클라이언트는 발급자 문서에서 읽은 토큰 엔드포인트 하나만 알고 그 주소는 찾아가지 못했다.
 *
 * <p><b>refresh 토큰은 본문에 싣는다. 헤더가 아니다.</b> 이 엔드포인트에서 {@code Authorization}
 * 헤더는 이미 다른 것에 쓰인다. 거기 실리는 것은 <b>클라이언트 인증</b>(client_id/secret)이지
 * 재발급할 토큰이 아니다. {@code Authorization: Bearer} 는 리소스 서버에 access 토큰을 낼 때
 * 쓰는 자리이고(RFC 6750), 발급자에게 무언가를 내는 자리가 아니다.
 *
 * <h2>모양을 우리가 정하지 않는다</h2>
 * 요청은 폼 인코딩, 응답은 {@code snake_case} JSON 이다. 우리 다른 API 는 {@code camelCase} 인데
 * 여기만 다른 이유는, 이 자리는 표준 OAuth 라이브러리가 그대로 읽을 수 있어야 하기 때문이다
 * (RFC 6749 4.1.4, 5.1). 오류 이름도 마찬가지다. 우리가 지은 단어를 쓰면 그쪽은 못 알아듣는다.
 *
 * <p>폼 인코딩에는 덤이 하나 있다. 브라우저가 {@code application/x-www-form-urlencoded} 를
 * 단순 요청으로 쳐서 preflight(OPTIONS)가 나가지 않는다. JSON 으로 받으면 요청마다 왕복이 하나 는다.
 *
 * <h2>만료(expires_in)를 싣지 않는다</h2>
 * access 토큰의 {@code exp} 클레임에 이미 있고, 두 군데에 두면 언젠가 어긋난다. 명세에서도
 * 권고일 뿐 필수는 아니다.
 *
 * <h2>실패는 사유를 나누지 않는다</h2>
 * 없는 코드든 만료든 남의 코드든, 이미 쓴 refresh 토큰이든 {@code invalid_grant} 하나다. 무엇이
 * 틀렸는지는 로그에만 남긴다. 응답에 적으면 주운 쪽이 그것으로 좁혀갈 수 있고, 정상적인 앱은
 * 어차피 로그인을 다시 시작하는 것 말고 할 일이 없다.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TokenEndpointController {

    private final AuthenticationRealm authenticationRealm;
    private final ExchangeAuthorizationCodeUseCase exchangeAuthorizationCode;
    private final RefreshTokenUseCase refreshToken;

    /**
     * 코드를 토큰으로 바꾼다. PKCE 원본은 모든 앱이 내고, 시크릿이 있는 앱은 {@code client_secret} 도
     * 본문에 함께 싣는다.
     */
    @PostMapping(path = "/realms/{realm}/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            params = "grant_type=authorization_code")
    public TokenResponse exchange(@PathVariable String realm,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                  @RequestParam(name = "client_id", required = false) String clientId,
                                  @RequestParam(name = "code_verifier", required = false) String codeVerifier,
                                  @RequestParam(name = "client_secret", required = false) String clientSecret) {
        Realm resolved = authenticationRealm.of(realm);
        return TokenResponse.of(exchangeAuthorizationCode.exchange(new ExchangeAuthorizationCodeCommand(
                resolved, code, clientId, redirectUri, codeVerifier, clientSecret)));
    }

    /**
     * refresh 토큰으로 새 토큰을 받는다. 낸 토큰은 이 호출로 죽는다({@code RefreshChain}).
     *
     * <p><b>{@code client_id} 를 읽지 않는다.</b> 새 access 토큰의 {@code aud} 는 경로의 realm 이
     * 정한다. 요청에서 받으면 토큰을 쥔 쪽이 향할 시스템을 갈아끼울 수 있다. 표준 클라이언트가
     * 실어 보내는 것은 막지 않되, 그 값으로 무엇을 정하지는 않는다.
     */
    @PostMapping(path = "/realms/{realm}/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            params = "grant_type=refresh_token")
    public TokenResponse refresh(@PathVariable String realm,
                                 @RequestParam(name = "refresh_token", required = false) String presented) {
        Realm resolved = authenticationRealm.of(realm);
        return TokenResponse.of(refreshToken.refresh(resolved, presented));
    }

    /**
     * 아는 {@code grant_type} 이 아니다.
     *
     * <p>위의 둘이 더 구체적인 매핑이라 먼저 걸리고, 남은 것만 여기로 온다. 이렇게 두면 아는
     * 방식마다 자기 파라미터만 선언하게 되어, 한 메서드가 두 요청의 파라미터를 모두 이고
     * 있지 않아도 된다.
     */
    @PostMapping(path = "/realms/{realm}/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public TokenResponse unsupported(@RequestParam(name = "grant_type", required = false) String grantType) {
        throw new UnsupportedGrantTypeException(grantType);
    }

    /** 코드나 refresh 토큰을 토큰으로 바꿀 수 없다. 사유는 로그에만 남는다. */
    @ExceptionHandler({InvalidGrantException.class, AuthenticationFailedException.class})
    ResponseEntity<TokenError> invalidGrant(RuntimeException e) {
        log.warn("토큰 발급 실패: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new TokenError("invalid_grant"));
    }

    /**
     * 앱이 자기가 그 앱임을 증명하지 못했다. 명세대로 401 이다(RFC 6749 5.2).
     *
     * <p>{@code WWW-Authenticate} 헤더는 싣지 않는다. 명세가 그것을 요구하는 것은 앱이
     * {@code Authorization} 헤더로 인증하려 했을 때인데, 우리는 그 헤더로 시크릿을 받지 않는다.
     */
    @ExceptionHandler(InvalidClientException.class)
    ResponseEntity<TokenError> invalidClient(InvalidClientException e) {
        log.warn("토큰 발급 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new TokenError("invalid_client"));
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
        log.warn("토큰 발급 실패: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new TokenError("invalid_request"));
    }

    static class UnsupportedGrantTypeException extends RuntimeException {
        UnsupportedGrantTypeException(String grantType) {
            super(String.valueOf(grantType));
        }
    }

    /**
     * 두 방식의 응답이 같은 모양이다. 재발급도 새 refresh 토큰을 함께 내려야 한다.
     * 회전 때문에 방금 쓴 토큰은 죽었고, 앱이 다음에 낼 것은 여기 실린 값이다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("refresh_token") String refreshToken,
                                @JsonProperty("token_type") String tokenType) {

        static TokenResponse of(AuthenticationResult result) {
            return new TokenResponse(result.tokens().accessToken(), result.tokens().refreshToken(), "Bearer");
        }
    }

    public record TokenError(String error) {
    }
}
