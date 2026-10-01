package com.brunosong.identityplatform.auth.service.domain.oauth;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 인가 코드 - "이 사람은 방금 우리 앞에서 로그인했다" 는 증서.
 *
 * <p>주소창에 실려 앱으로 건너간다. 그래서 이것 자체는 아무 힘이 없어야 한다. 이 값을 주운
 * 사람이 토큰을 받을 수 있으면 로그인 화면을 거친 의미가 없다. 그래서 세 겹으로 묶어 둔다.
 *
 * <ul>
 *   <li><b>짧다.</b> 1분이면 앱이 토큰으로 바꾸기에 넉넉하고, 주워서 쓰기에는 짧다.</li>
 *   <li><b>한 번 쓴다.</b> 두 번째 사용은 저장소에서 막는다 - 꺼내면서 지운다.</li>
 *   <li><b>임자가 있다.</b> 시작할 때의 앱, 그때의 주소, 그리고 PKCE 원본을 쥔 쪽만 쓴다.</li>
 * </ul>
 */
@Getter
public class AuthorizationCode {

    /**
     * 수명. 앱이 받자마자 토큰으로 바꾸므로 길 이유가 없다.
     *
     * <p>명세도 10분을 넘기지 말라고 하고 1분 이내를 권한다(RFC 6749 4.1.2).
     */
    private static final Duration LIFETIME = Duration.ofMinutes(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 주소창에 실려 나가는 값. 추측할 수 없어야 하므로 난수다. */
    private final String code;

    private final Realm realm;
    private final String clientId;

    /** 시작할 때 적혀 온 주소. 토큰을 바꾸러 올 때 같은 값을 내야 한다. */
    private final String redirectUri;

    /** PKCE 검증값의 해시. 원본은 앱만 쥐고 있다. */
    private final String codeChallenge;

    /** 누가 로그인했는가. 토큰은 이 사람 앞으로 나간다. */
    private final PrincipalId principalId;

    private final String scope;
    private final String nonce;
    private final Instant expiresAt;

    private AuthorizationCode(String code, Realm realm, String clientId, String redirectUri,
                              String codeChallenge, PrincipalId principalId, String scope,
                              String nonce, Instant expiresAt) {
        this.code = code;
        this.realm = realm;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.codeChallenge = codeChallenge;
        this.principalId = principalId;
        this.scope = scope;
        this.nonce = nonce;
        this.expiresAt = expiresAt;
    }

    /** 로그인이 성립한 직후에 발급한다. 인가 요청이 들고 온 값들을 그대로 묶어 둔다. */
    public static AuthorizationCode issue(Realm realm, AuthorizationRequest request,
                                          PrincipalId principalId, Instant now) {
        return new AuthorizationCode(randomCode(), realm, request.getClientId(),
                request.getRedirectUri(), request.getCodeChallenge(), principalId,
                request.getScope(), request.getNonce(), now.plus(LIFETIME));
    }

    public static AuthorizationCode restore(String code, Realm realm, String clientId,
                                            String redirectUri, String codeChallenge,
                                            PrincipalId principalId, String scope, String nonce,
                                            Instant expiresAt) {
        return new AuthorizationCode(code, realm, clientId, redirectUri, codeChallenge, principalId,
                scope, nonce, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * 로그인 결과를 id_token 으로도 달라고 했는가. scope 에 {@code openid} 가 낱말로 있어야 한다.
     *
     * <p>scope 는 공백으로 나뉜 낱말 목록이다(RFC 6749 3.3). 글자 포함으로 보면 {@code openid2}
     * 같은 값에도 id_token 이 나간다.
     */
    public boolean requestsOpenId() {
        return scope != null && List.of(scope.split(" ")).contains("openid");
    }

    /**
     * 토큰으로 바꾸러 온 쪽이 이 코드의 임자인지 본다.
     *
     * <p>앱과 주소를 다시 대조하는 것은, 코드가 <b>다른 앱의 손에 들어간 경우</b>를 막기 위해서다.
     * 주소를 대조하는 이유는 명세가 시작할 때와 같은 값을 요구하기 때문이다(RFC 6749 4.1.3).
     *
     * <p>PKCE 는 <b>같은 브라우저인지</b>를 본다. 원본을 해시해서 시작할 때 받아둔 값과 맞춘다.
     * 코드를 주운 쪽은 원본을 모르므로 여기서 걸린다.
     */
    public boolean belongsTo(String clientId, String redirectUri, String codeVerifier) {
        return this.clientId.equals(clientId)
                && this.redirectUri.equals(redirectUri)
                && codeVerifier != null
                && this.codeChallenge.equals(Pkce.challengeOf(codeVerifier));
    }

    private static String randomCode() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
