package com.brunosong.identityplatform.auth.service.domain.oauth;

import lombok.Getter;

/**
 * 인가 요청 - 앱이 "이 사람을 로그인시켜 달라" 며 보내온 값들.
 *
 * <p>주소창의 질의 문자열이 그대로 들어온다. 전부 요청자가 적어 보낸 값이라 하나도 믿지 않고
 * 여기서 한 번 거른다. 거르는 것은 <b>규약</b>이고(무엇을 달라는 요청인가), 그 앱이 등록된
 * 앱인지는 {@link OAuthClient} 가 본다.
 */
@Getter
public class AuthorizationRequest {

    private final String clientId;
    private final String redirectUri;
    private final String scope;

    /** 앱이 돌아올 때 그대로 돌려받는 값. 앱이 자기가 시작한 요청인지 확인하는 데 쓴다. */
    private final String state;

    /** PKCE 검증값의 해시. 원본은 토큰을 바꾸러 올 때 낸다. */
    private final String codeChallenge;

    /** id_token 에 그대로 실어 보낼 값. 앱이 토큰 재생을 가려내는 데 쓴다. */
    private final String nonce;

    private AuthorizationRequest(String clientId, String redirectUri, String scope, String state,
                                 String codeChallenge, String nonce) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.scope = scope;
        this.state = state;
        this.codeChallenge = codeChallenge;
        this.nonce = nonce;
    }

    /**
     * 받아들일 수 있는 요청인지 보고 만든다.
     *
     * <p>받는 것은 code 를 달라는 요청 하나뿐이다. 토큰을 바로 달라는 요청(implicit)은 받지
     * 않는다 - 토큰이 주소창에 실려 오면 브라우저 기록과 리퍼러에 남는다.
     *
     * <p>PKCE 는 선택이 아니다. 등록되는 앱이 전부 시크릿 없는 public client 라, 이것이 없으면
     * code 를 가로챈 쪽을 걸러낼 수단이 하나도 없다.
     */
    public static AuthorizationRequest of(String responseType, String clientId, String redirectUri,
                                          String scope, String state,
                                          String codeChallenge, String codeChallengeMethod,
                                          String nonce) {
        if (!"code".equals(responseType)) {
            throw new IllegalArgumentException("response_type 은 code 만 받는다: " + responseType);
        }
        if (isBlank(clientId)) {
            throw new IllegalArgumentException("client_id 가 없다");
        }
        if (isBlank(redirectUri)) {
            throw new IllegalArgumentException("redirect_uri 가 없다");
        }
        if (isBlank(codeChallenge)) {
            throw new IllegalArgumentException("code_challenge 가 없다");
        }
        // plain 은 해시하지 않고 값을 그대로 보내는 방식이라, 요청을 들여다본 쪽이 그대로 흉내낸다.
        if (!"S256".equals(codeChallengeMethod)) {
            throw new IllegalArgumentException("code_challenge_method 는 S256 만 받는다: " + codeChallengeMethod);
        }
        return new AuthorizationRequest(clientId.trim(), redirectUri.trim(), trimToNull(scope),
                trimToNull(state), codeChallenge.trim(), trimToNull(nonce));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
