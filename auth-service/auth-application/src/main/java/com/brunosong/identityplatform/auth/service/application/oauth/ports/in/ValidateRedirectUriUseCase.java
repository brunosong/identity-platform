package com.brunosong.identityplatform.auth.service.application.oauth.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 이 앱에 등록된 주소인지 묻는다.
 *
 * <p>로그인 흐름은 이것을 따로 묻지 않는다. 인가 요청을 받아들일지 판단하면서 함께 보기 때문이다.
 * 따로 필요해진 것은 <b>로그아웃</b>이다 - 세션을 끊은 뒤 사람을 앱으로 돌려보내는데, 그 주소도
 * 요청자가 적어 보낸 값이라 그대로 믿을 수 없다.
 *
 * <p>대조하는 목록은 로그인 때 쓰는 그 목록이다. OIDC 는 로그아웃용 주소를 따로 등록하게 하고
 * 그럴 이유도 있다 - 이 목록에 있는 주소는 인가 코드도 받을 수 있는 주소이기 때문이다.
 * 목록이 둘로 갈리는 날 이 메서드가 그쪽을 보게 된다.
 */
public interface ValidateRedirectUriUseCase {

    boolean isRegistered(Realm realm, String clientId, String redirectUri);
}
