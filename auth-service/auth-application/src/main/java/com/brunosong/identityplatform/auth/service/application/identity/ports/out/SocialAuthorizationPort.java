package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;

import java.net.URI;

/**
 * 브라우저를 provider 로 보낼 주소를 만드는 드리븐 포트.
 *
 * <p>{@link SocialIdentityVerifierPort} 와 짝이다. 이쪽이 보내는 길이고 그쪽이 돌아온 것을 확인하는
 * 길이다. 둘 다 provider 하나를 아웃바운드 어댑터가 구현한다 - auth 는 구글 주소도, 파라미터 이름도,
 * 스코프 문자열도 모른다.
 *
 * <p>주소를 만드는 일을 web 계층에 두지 않는 이유가 있다. 그러면 컨트롤러가 provider 의 사정을
 * 알게 되고, provider 가 늘 때마다 컨트롤러가 갈라진다. 무엇을 보내야 하는지는 provider 마다 다르고,
 * 그 차이는 어댑터 안에 있어야 한다.
 */
public interface SocialAuthorizationPort {

    /**
     * 이 provider 의 인가 요청 주소. 여기로 302 를 보내면 브라우저가 provider 의 로그인 화면으로 간다.
     *
     * @param state 이 흐름을 시작한 브라우저를 표시하는 난수. provider 가 그대로 돌려주므로,
     *              돌아온 값이 이 브라우저가 시작한 것인지 대조할 수 있다. 만들고 보관하는 일은
     *              브라우저를 쥔 쪽(web)이 한다 - 어댑터는 실어 보내기만 한다.
     */
    URI authorizationUri(SocialProvider provider, String state);
}
