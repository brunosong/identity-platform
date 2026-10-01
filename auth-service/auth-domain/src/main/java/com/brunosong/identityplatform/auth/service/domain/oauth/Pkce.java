package com.brunosong.identityplatform.auth.service.domain.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * PKCE 의 S256 규칙(RFC 7636 4.2). 원본(verifier)을 해시해 challenge 를 만든다.
 *
 * <p>한 곳에 둔다. 로그인을 시작하는 쪽(운영 화면)과 code 를 바꿔 줄 때 대조하는 쪽
 * ({@link AuthorizationCode})이 같은 규칙을 써야 하는데, 두 군데 적으면 한쪽만 고쳤을 때 로그인이 깨진다.
 */
public final class Pkce {

    private Pkce() {
    }

    /** base64url(sha256(verifier)). 패딩 없이 적는다. */
    public static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 갖춰야 하는 알고리즘이다. 없으면 실행 환경이 깨진 것이다.
            throw new IllegalStateException(e);
        }
    }
}
