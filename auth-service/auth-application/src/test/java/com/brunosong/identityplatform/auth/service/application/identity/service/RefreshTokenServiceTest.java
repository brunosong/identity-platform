package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakeTokenIssuer;
import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리프레시 재발급 — 토큰이 가리키는 주체가 실제로 있어야만 새 토큰이 나간다.
 */
class RefreshTokenServiceTest {

    private FakePrincipalRepository principalRepo;
    private FakeTokenIssuer tokenIssuer;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        principalRepo = new FakePrincipalRepository();
        principalRepo.seed("customer-uuid-1", SubjectType.CUSTOMER);
        tokenIssuer = new FakeTokenIssuer();
        service = new RefreshTokenService(principalRepo, new TokenIssuance(tokenIssuer));
    }

    @Test
    @DisplayName("리프레시 토큰의 주체가 살아 있으면 새 토큰쌍을 발급한다")
    void refreshIssuesNewTokens() {
        tokenIssuer.refreshTokenSubjectId = "customer-uuid-1";

        AuthenticationResult result = service.refresh(SubjectType.CUSTOMER, "refresh:customer-uuid-1");

        assertThat(result.subjectId()).isEqualTo("customer-uuid-1");
        assertThat(result.tokens().accessToken()).isEqualTo("access:CUSTOMER:customer-uuid-1");
    }

    @Test
    @DisplayName("토큰이 가리키는 주체가 없으면 재발급하지 않는다")
    void unknownSubjectRejected() {
        tokenIssuer.refreshTokenSubjectId = "gone-uuid";

        assertThatThrownBy(() -> service.refresh(SubjectType.CUSTOMER, "refresh:gone-uuid"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("유효하지 않습니다");
    }

}
