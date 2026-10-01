package com.brunosong.identityplatform.auth.service.domain.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceTest {

    @Test
    @DisplayName("RFC 7636 부록 B 의 예시 한 쌍과 같은 값을 낸다")
    void matchesRfcExample() {
        assertThat(Pkce.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }
}
