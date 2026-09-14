package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 주체에게 역할 하나를 가산 부여한다. 기존 역할은 유지한다.
 *
 * @throws AuthorizationNotFoundException 그 realm 에 해당 코드의 역할이 없을 때. 권한 없는 계정이
 *         조용히 만들어지는 것보다 실패가 낫다 — 로그인은 되는데 아무 데도 못 들어가는 계정은
 *         원인을 찾기 어렵다.
 */
public interface GrantRoleUseCase {

    /** realm 공통 역할을 부여한다. */
    default void grant(Realm realm, String subjectId, String roleCode) {
        grant(realm, subjectId, null, roleCode);
    }

    /** {@code clientId} 가 null 이면 realm 공통 역할, 값이 있으면 그 서비스의 역할을 부여한다. */
    void grant(Realm realm, String subjectId, String clientId, String roleCode);
}
