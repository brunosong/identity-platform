package com.brunosong.identityplatform.auth.service.social.google;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialAuthorizationPort;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * {@link SocialAuthorizationPort} 구글 구현 - 브라우저를 구글 로그인 화면으로 보낼 주소를 만든다.
 *
 * <p>여기서 만든 주소로 302 를 보내면 브라우저는 우리를 떠난다. 그다음 벌어지는 일(아이디 입력,
 * 비밀번호, 2단계 인증, 동의)은 전부 구글 쪽에서 일어나고 우리는 보지 못한다. <b>비밀번호가 우리
 * 서버를 거치지 않는다</b>는 말이 이 한 번의 리다이렉트에서 나온다.
 *
 * <p>주소를 코드에 박아 두었다. 구글도 discovery 문서({@code accounts.google.com/.well-known/openid-configuration})로
 * 엔드포인트를 알려주니 거기서 받아오는 것이 정석이지만, 그러려면 부팅이나 첫 로그인에 왕복이
 * 하나 붙는다. 뒤에 JWKS 주소가 필요해지면 그때 discovery 를 한 번에 들이는 편이 낫다고 보고
 * 지금은 미뤘다.
 */
class GoogleAuthorizationAdapter implements SocialAuthorizationPort {

    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";

    /**
     * openid 를 넣어야 응답에 {@code id_token} 이 실린다. 그게 없으면 사람 정보를 알아내려고
     * userinfo 를 따로 불러야 한다. email 과 profile 은 그 토큰에 주소와 이름을 담아 달라는 뜻이다.
     *
     * <p>이 이상 요구하지 않는다. 우리가 이 로그인에서 알아야 할 것은 "이 사람이 누구인가" 뿐이고,
     * 더 요구하면 사용자가 보는 동의 화면만 무거워진다.
     */
    private static final String SCOPE = "openid email profile";

    private final GoogleClientRegistration registration;

    GoogleAuthorizationAdapter(GoogleClientRegistration registration) {
        this.registration = registration;
    }

    @Override
    public URI authorizationUri(SocialProvider provider, String state) {
        if (provider != SocialProvider.GOOGLE) {
            throw new IllegalArgumentException("이 어댑터는 구글만 상대한다: " + provider);
        }
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", registration.clientId())
                // 돌아올 주소. 구글 콘솔에 등록해 둔 값과 글자 그대로 같아야 한다.
                .queryParam("redirect_uri", registration.brokerRedirectUri())
                // code 를 달라는 것. 토큰을 바로 달라고 하던 방식(implicit)은 토큰이 주소창에
                // 실려 오는 문제 때문에 쓰지 않는다.
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                // 구글은 이 값을 해석하지 않고 그대로 돌려준다. 뜻은 우리만 안다.
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }
}
