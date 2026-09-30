package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.RefreshChainRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.RefreshedToken;
import com.brunosong.identityplatform.auth.service.application.identity.token.TokenProperties;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.RefreshChain;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 토큰 발급 결과를 만드는 곳. 발급 자체는 {@link TokenIssuerPort} 가 한다.
 *
 * <p>발급기는 이제 필수 의존이다. 전에는 호스트가 프로퍼티로 켜는 선택 빈이라 "지연 조회 → null 이면
 * 설정 오류로 실패" 라는 절차가 필요했고, 그것이 로그인 방식마다 복사돼 있었다. 독립 서비스에서는
 * 토큰 발급이 본래 기능이라 없을 수가 없다 — 없으면 부팅이 실패하는 편이 맞다.
 *
 * <h2>refresh 토큰의 계보도 여기서 관리한다</h2>
 * 로그인이면 계보를 하나 열고, 재발급이면 그 계보를 회전시킨다({@link RefreshChain}). 두 가지가
 * 한 클래스에 있는 이유는 <b>토큰을 만드는 자리와 계보를 적는 자리가 갈리면 안 되기</b> 때문이다.
 * 갈리면 토큰에는 실렸는데 저장은 안 된 계보, 또는 그 반대가 생기고, 그 어긋남은 다음 재발급에서야
 * 드러난다.
 */
@Component
@RequiredArgsConstructor
class TokenIssuance {

    private final TokenIssuerPort tokenIssuer;
    private final RefreshChainRepository refreshChains;
    private final TokenProperties tokenProperties;

    /** 인증된 주체에게 그 realm 의 토큰을 발급해 인증 결과로 만든다. 계보가 하나 열린다. */
    AuthenticationResult resultFor(Realm realm, Principal principal) {
        RefreshChain chain = RefreshChain.start(realm, principal.getSubjectId().value(),
                Instant.now(), refreshLifetime());
        refreshChains.save(chain);
        return resultWith(realm, principal, chain);
    }

    /**
     * 재발급. <b>낸 토큰이 계보의 현재 것일 때만</b> 새 토큰이 나간다.
     *
     * <p>이미 회전된 토큰이 다시 오면 그 계보를 통째로 지운다. 훔친 쪽이 쓴 것인지 진짜 사용자가
     * 뒤늦게 쓴 것인지 가릴 수 없고, 가릴 수 없으면 둘 다 끊는 편이 맞다. 진짜 사용자는 다시
     * 로그인하면 되지만, 살려두면 훔친 쪽도 계속 쓴다.
     */
    AuthenticationResult rotatedResultFor(Realm realm, Principal principal, RefreshedToken presented) {
        RefreshChain chain = refreshChains.findById(presented.familyId())
                // 서명이 맞아도 남의 계보를 지목했으면 없는 것으로 친다.
                .filter(found -> found.belongsTo(realm, presented.subjectId()))
                .orElseThrow(TokenIssuance::invalidRefreshToken);

        if (!chain.isCurrent(presented.jti())) {
            refreshChains.revoke(chain.getFamilyId());
            throw invalidRefreshToken();
        }

        RefreshChain rotated = chain.rotate(Instant.now(), refreshLifetime());
        refreshChains.save(rotated);
        return resultWith(realm, principal, rotated);
    }

    /** 그 토큰의 계보를 끊는다. 남의 계보를 지목했으면 건드리지 않는다. 끊으면 남의 로그인을 끊는 길이 된다. */
    void revoke(Realm realm, RefreshedToken presented) {
        refreshChains.findById(presented.familyId())
                .filter(found -> found.belongsTo(realm, presented.subjectId()))
                .ifPresent(chain -> refreshChains.revoke(chain.getFamilyId()));
    }

    /** refresh 토큰에서 주체와 계보를 읽는다. 검증은 키를 쥔 발급기가 한다. */
    RefreshedToken readRefreshToken(Realm realm, String refreshToken) {
        return tokenIssuer.readRefreshToken(realm, refreshToken);
    }

    private AuthenticationResult resultWith(Realm realm, Principal principal, RefreshChain chain) {
        return new AuthenticationResult(
                principal.getPrincipalId().value(),
                principal.getSubjectId().value(),
                principal.getRealm(),
                tokenIssuer.issue(realm, principal, chain));
    }

    /** 계보의 수명은 refresh 토큰의 수명과 같다. 토큰은 살아 있는데 계보가 없으면 재발급이 안 된다. */
    private Duration refreshLifetime() {
        return Duration.ofMillis(tokenProperties.getRefreshExpiration());
    }

    /** 사유를 나누지 않는다. 무엇이 틀렸는지 알려주면 낸 쪽이 그것으로 상태를 좁혀갈 수 있다. */
    private static AuthenticationFailedException invalidRefreshToken() {
        return new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
    }
}
