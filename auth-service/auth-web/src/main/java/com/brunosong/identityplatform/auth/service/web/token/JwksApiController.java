package com.brunosong.identityplatform.auth.service.web.token;

import com.brunosong.identityplatform.auth.service.application.identity.token.RealmSigningKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWKS — 이 서비스가 발급한 토큰을 <b>다른 서비스가 검증할 수 있게</b> 공개키를 내보낸다.
 *
 * <p>이것이 MSA 에서 인증을 나누는 방식이다. 다른 서비스는 auth 에 요청을 보내 토큰을 물어보지 않는다
 * (요청마다 왕복이 생기고 auth 가 단일 장애점이 된다). 공개키만 받아다 자기가 서명을 검증한다 —
 * auth 가 잠시 죽어도 이미 발급된 토큰은 계속 검증된다.
 *
 * <p>공개키를 설정 파일로 배포하지 않는 이유는 키 교체다. 파일로 뿌리면 키를 바꿀 때 모든 서비스를
 * 다시 배포해야 한다. JWKS 로 두면 auth 만 바꾸고, 소비 서비스는 모르는 {@code kid} 를 만났을 때
 * 다시 받아오면 된다.
 *
 * <p>두 realm 의 키가 한 문서에 함께 나간다. 공개키는 비밀이 아니고, 어느 키로 검증할지는 토큰 헤더의
 * {@code kid} 가 정한다. 다만 <b>서명이 맞다고 realm 이 맞는 것은 아니다</b> — 소비 서비스는 서명 검증
 * 뒤에 {@code realm} 클레임까지 확인해야 한다. 그러지 않으면 직원 토큰으로 고객 API 를 통과한다.
 *
 * <p>인증이 필요 없는 공개 엔드포인트다. 여기 담긴 것은 공개키뿐이라 숨길 것이 없다.
 */
@RestController
@RequiredArgsConstructor
public class JwksApiController {

    /** 소비 서비스가 캐시할 시간. 키 교체 시 옛 kid 가 이 시간만큼 더 쓰일 수 있음을 감안해 정한다. */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final RealmSigningKeys signingKeys;

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        List<Map<String, Object>> keys = new ArrayList<>();
        signingKeys.verifyKeys().forEach((kid, key) -> keys.add(toJwk(kid, key)));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .body(Map.of("keys", keys));
    }

    /**
     * RSA 공개키를 JWK 로 옮긴다(RFC 7517/7518). 키는 모듈러스 {@code n} 과 지수 {@code e} 두 값이면 된다.
     *
     * <p>JWK 는 순서 있는 맵으로 만든다 — 응답이 매번 같은 모양이라 캐시·디프가 안정적이다.
     */
    private static Map<String, Object> toJwk(String kid, PublicKey key) {
        RSAPublicKey rsa = (RSAPublicKey) key;

        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", kid);
        jwk.put("n", base64Url(rsa.getModulus()));
        jwk.put("e", base64Url(rsa.getPublicExponent()));
        return jwk;
    }

    /**
     * JWK 의 수는 부호 없는 big-endian 을 base64url(패딩 없음)로 적는다.
     *
     * <p>{@link BigInteger#toByteArray()} 는 부호 비트를 위해 앞에 0x00 을 하나 붙일 때가 있다.
     * 그대로 내보내면 값이 한 바이트 길어져 다른 라이브러리가 키를 잘못 읽는다.
     */
    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
