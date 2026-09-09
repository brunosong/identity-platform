package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.application.identity.token.RealmSigningKeys;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * auth 가 발급한 access 토큰을 검증(서명·만료)하고 claims 를 읽는다. 무효/만료 토큰은 빈 값으로 다룬다
 * (예외 대신 Optional.empty — 비로그인/만료를 정상 흐름으로 처리).
 *
 * <h2>realm 을 먼저 정하고, 그 realm 의 키로만 검증한다</h2>
 * 검증 키를 {@code kid} 로 고르지 않는다. 이 서비스는 두 realm 의 키를 <b>다</b> 쥐고 있어서, kid 를
 * 따라가면 어느 realm 의 토큰이든 서명이 통과한다. 그러면 "이 토큰이 어느 realm 것인가"를 다시
 * 클레임에 물어봐야 하는데, 클레임은 토큰이 스스로 하는 주장이다.
 *
 * <p>realm 을 먼저 받아 그 realm 의 공개키 하나로만 검증하면 <b>realm 을 서명이 증명한다.</b>
 * 다른 realm 의 토큰은 여기서 죽는다 — 클레임을 읽어보기도 전에.
 *
 * <p>호출자가 realm 을 정한다고 해서 realm 을 사칭할 수 있는 것은 아니다. 경로는 realm 을
 * <b>주장</b>하는 것이 아니라 <b>검증 키를 고를</b> 뿐이라, 잘못 지목하면 그 키로 검증에 실패한다.
 * (Keycloak 도 모든 엔드포인트가 {@code /realms/{realm}/...} 이고 realm 을 토큰보다 먼저 정한다.)
 */
@Component
@RequiredArgsConstructor
public class AccessTokenReader {

    private final RealmSigningKeys signingKeys;

    /** 그 realm 의 공개키로만 검증한다. 다른 realm 의 토큰이면 빈 값이다. */
    public Optional<Claims> read(Realm realm, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKeys.of(realm).publicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
