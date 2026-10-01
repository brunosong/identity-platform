package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.UserInfo;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Optional;

/**
 * 토큰 주인이 누구인지 사람이 읽는 정보로 돌려준다. OIDC 의 userinfo 엔드포인트가 부른다.
 *
 * <p>그 realm 에 그 주체가 없으면 비어 있다. 토큰은 유효한데 신원이 지워진 경우다.
 */
public interface FindUserInfoUseCase {

    Optional<UserInfo> of(Realm realm, String subjectId);
}
