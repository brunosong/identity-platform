package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 로그인·재발급 응답에 실리는 토큰. 모든 로그인 방식이 같은 모양으로 돌려준다.
 *
 * <p><b>쿠키가 아니라 본문으로 내린다.</b> 전에는 {@code httpOnly} 쿠키에 실었다 — 스크립트가 토큰을
 * 읽을 수 없어 XSS 에 강했다. 그런데 이 서비스와 프론트엔드는 도메인이 다르고, 인증 서버 도메인에 구운
 * 쿠키는 다른 도메인의 프론트에 전달되지 않는다. 서비스가 여럿인 구성에서는 쿠키로 토큰을 나를 수 없다.
 *
 * <p>그래서 access 토큰은 본문으로 내리고 이후 요청은 {@code Authorization: Bearer} 로 싣는다
 * (OAuth/OIDC 방식). 대가는 분명하다. 토큰이 스크립트가 읽을 수 있는 자리에 놓이므로 XSS 면 털린다.
 * 완화는 호출자 몫이다: access 토큰은 짧게 쓰고 메모리에 두며, {@code localStorage} 에 넣지 않는다.
 *
 * <p><b>refresh 토큰은 갈라져 나갔다.</b> 그것이 갈 곳은 재발급 엔드포인트 하나뿐이고 그곳은 우리
 * 자신이라, 도메인이 갈리는 문제가 없다. 그래서 인가 코드 흐름에서는 {@code HttpOnly} 쿠키로 내보내고
 * 본문에서 뺀다({@link RefreshTokenCookie}). 나머지 로그인 경로는 아직 본문으로 내린다.
 *
 * <p>만료는 따로 싣지 않는다. access 토큰의 {@code exp} 클레임에 이미 있고, 두 군데에 두면 어긋난다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssuedTokens(String tokenType, String accessToken, String refreshToken) {

    public static IssuedTokens of(TokenPair tokens) {
        return new IssuedTokens("Bearer", tokens.accessToken(), tokens.refreshToken());
    }

    /**
     * refresh 토큰이 {@code Set-Cookie} 로 나간 경우. 본문에서는 뺀다.
     *
     * <p>둘 다 주면 스크립트가 본문에서 읽어 보관할 수 있고, 그러면 {@code HttpOnly} 는 장식이 된다.
     */
    public static IssuedTokens withoutRefresh(TokenPair tokens) {
        return new IssuedTokens("Bearer", tokens.accessToken(), null);
    }
}
