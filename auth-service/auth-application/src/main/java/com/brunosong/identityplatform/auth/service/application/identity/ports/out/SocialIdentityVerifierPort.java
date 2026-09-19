package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.VerifiedSocialIdentity;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialCallback;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;

/**
 * 소셜 provider 로 authorizationCode 를 교환·검증해 확인된 외부 신원을 돌려주는 드리븐 포트.
 *
 * <p>실제 OAuth 통신(코드 교환, id_token 검증, 스코프)은 호스트가 소유한다 — auth 는 provider 라이브러리를
 * 모른다({@code TokenIssuerPort}/{@code OtpEmailSenderPort} 와 같은 결의 아웃바운드). 이 포트를 제공하는
 * 호스트에서만 소셜 로그인이 활성화된다(미제공 시 소셜 로그인 미지원).
 */
public interface SocialIdentityVerifierPort {

    /**
     * @param callback provider 가 브라우저를 어디로 돌려보냈는지. 교환 요청에 실을
     *                 {@code redirect_uri} 가 그 주소여야 provider 가 받아준다.
     */
    VerifiedSocialIdentity verify(SocialProvider provider, String authorizationCode,
                                  SocialCallback callback);
}
