package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.application.identity.token.RsaKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.util.Optional;

/**
 * auth 가 발급한 access 토큰을 검증(서명·만료)하고 claims 를 읽는다. 무효/만료 토큰은 빈 값으로 다룬다
 * (예외 대신 Optional.empty — 비로그인/만료를 정상 흐름으로 처리).
 *
 * <p>토큰 판독 로직을 한곳에 모아 컨트롤러가 jjwt/시크릿을 직접 다루지 않게 한다.
 */
@Component
public class AccessTokenReader {

    private final PublicKey verifyKey;

    public AccessTokenReader(@Value("${token.publicKey}") String publicKeyBase64) {
        this.verifyKey = RsaKeys.publicKeyFromBase64Der(publicKeyBase64);
    }

    public Optional<Claims> read(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser().verifyWith(verifyKey).build()
                    .parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
