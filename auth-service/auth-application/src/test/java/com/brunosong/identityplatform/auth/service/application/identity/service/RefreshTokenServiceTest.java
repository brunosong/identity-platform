package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.RefreshedToken;
import com.brunosong.identityplatform.auth.service.application.identity.token.TokenProperties;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.RefreshChain;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeRefreshChains;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeTokenIssuer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리프레시 재발급 — 토큰이 가리키는 주체가 실제로 있어야, 그리고 그 토큰이 계보의 현재 것이어야
 * 새 토큰이 나간다.
 */
class RefreshTokenServiceTest {

    private FakePrincipalRepository principalRepo;
    private FakeTokenIssuer tokenIssuer;
    private FakeRefreshChains chains;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        principalRepo = new FakePrincipalRepository();
        principalRepo.seed("customer-uuid-1", Realm.PORTAL);
        tokenIssuer = new FakeTokenIssuer();
        chains = new FakeRefreshChains();
        service = new RefreshTokenService(principalRepo,
                new TokenIssuance(tokenIssuer, chains, new TokenProperties()));
    }

    @Test
    @DisplayName("리프레시 토큰의 주체가 살아 있으면 새 토큰쌍을 발급한다")
    void refreshIssuesNewTokens() {
        present(openChain("customer-uuid-1"), "customer-uuid-1");

        AuthenticationResult result = service.refresh(Realm.PORTAL, "refresh:customer-uuid-1");

        assertThat(result.subjectId()).isEqualTo("customer-uuid-1");
        assertThat(result.tokens().accessToken()).isEqualTo("access:PORTAL:customer-uuid-1");
    }

    @Test
    @DisplayName("재발급하면 계보의 현재 토큰이 갈아끼워진다")
    void refreshRotatesTheChain() {
        RefreshChain opened = openChain("customer-uuid-1");
        present(opened, "customer-uuid-1");

        service.refresh(Realm.PORTAL, "refresh:customer-uuid-1");

        // 계보는 그대로고 그 안의 토큰만 바뀐다. 방금 낸 토큰은 이제 옛것이다.
        RefreshChain rotated = chains.byFamilyId.get(opened.getFamilyId());
        assertThat(rotated.getFamilyId()).isEqualTo(opened.getFamilyId());
        assertThat(rotated.getCurrentJti()).isNotEqualTo(opened.getCurrentJti());
        assertThat(tokenIssuer.issuedChain.getCurrentJti()).isEqualTo(rotated.getCurrentJti());
    }

    @Test
    @DisplayName("이미 쓴 토큰이 다시 오면 그 계보를 통째로 끊는다")
    void reusedTokenRevokesTheWholeChain() {
        RefreshChain opened = openChain("customer-uuid-1");
        present(opened, "customer-uuid-1");
        service.refresh(Realm.PORTAL, "refresh:customer-uuid-1");

        // 방금 쓴 토큰을 다시 낸다. 훔친 쪽인지 진짜 사용자인지 가릴 수 없는 상황이다.
        present(opened, "customer-uuid-1");

        assertThatThrownBy(() -> service.refresh(Realm.PORTAL, "refresh:customer-uuid-1"))
                .isInstanceOf(AuthenticationFailedException.class);

        // 계보가 사라졌으므로 직전에 나간 새 토큰으로도 재발급되지 않는다.
        assertThat(chains.byFamilyId).doesNotContainKey(opened.getFamilyId());
    }

    @Test
    @DisplayName("남의 계보를 지목한 토큰은 거절한다")
    void chainOfAnotherSubjectRejected() {
        RefreshChain someoneElse = openChain("customer-uuid-2");
        present(someoneElse, "customer-uuid-1");

        assertThatThrownBy(() -> service.refresh(Realm.PORTAL, "refresh:customer-uuid-1"))
                .isInstanceOf(AuthenticationFailedException.class);

        // 남의 계보를 지목했다고 그 계보가 끊기면, 남의 로그인을 끊는 길이 된다.
        assertThat(chains.byFamilyId).containsKey(someoneElse.getFamilyId());
    }

    @Test
    @DisplayName("토큰이 가리키는 주체가 없으면 재발급하지 않는다")
    void unknownSubjectRejected() {
        present(openChain("gone-uuid"), "gone-uuid");

        assertThatThrownBy(() -> service.refresh(Realm.PORTAL, "refresh:gone-uuid"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("유효하지 않습니다");
    }

    /** 로그인해서 계보가 하나 열린 상태를 만든다. */
    private RefreshChain openChain(String subjectId) {
        RefreshChain chain = RefreshChain.start(Realm.PORTAL, subjectId, Instant.now(), Duration.ofDays(1));
        chains.save(chain);
        return chain;
    }

    /** 그 계보의 현재 토큰을 들고 온 것으로 친다. */
    private void present(RefreshChain chain, String subjectId) {
        tokenIssuer.presented = new RefreshedToken(subjectId, chain.getFamilyId(), chain.getCurrentJti());
    }
}
