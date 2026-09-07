package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.application.identity.token.RealmSigningKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Optional;

/**
 * auth 가 발급한 access 토큰을 검증(서명·만료)하고 claims 를 읽는다. 무효/만료 토큰은 빈 값으로 다룬다
 * (예외 대신 Optional.empty — 비로그인/만료를 정상 흐름으로 처리).
 *
 * <p>검증 키는 JWT 헤더의 {@code kid} 로 고른다. 한 서비스가 realm 별로 다른 키를 쓰므로 공개키가 여러
 * 벌이고, 어느 것으로 검증할지는 토큰을 열어보기 전에 정해야 한다 — 내용을 믿으려면 먼저 서명을 확인해야
 * 하기 때문이다. 그래서 kid 는 서명 대상 밖인 헤더에 있다. 전에는 공개키가 하나뿐이라 고를 일이 없었다.
 *
 * <p>모르는 kid 는 이 서비스가 발급하지 않은 토큰이므로 그냥 빈 값이 된다.
 *
 * <p>여기서 얻은 claims 의 {@code realm} 이 호출자의 realm 이다. kid 로 realm 을 유추하지 않는다 —
 * kid 는 키 교체 때 바뀌는 값이고, realm 은 클레임으로 서명 안에 들어 있다.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenReader {

    private final RealmSigningKeys signingKeys;

    public Optional<Claims> read(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .keyLocator((io.jsonwebtoken.Header header) -> keyFor(header.get("kid")))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Key keyFor(Object kid) {
        return kid == null ? null : signingKeys.verifyKeyOf(kid.toString());
    }
}
