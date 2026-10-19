package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import java.util.List;

/**
 * 주체에게 붙은 역할 코드를 모두 준다(활성 역할만). access 토큰의 {@code realm_access.roles} 에 실을 값이다.
 */
public interface ListSubjectRolesUseCase {

    List<String> roleCodes(Realm realm, String subjectId);
}
