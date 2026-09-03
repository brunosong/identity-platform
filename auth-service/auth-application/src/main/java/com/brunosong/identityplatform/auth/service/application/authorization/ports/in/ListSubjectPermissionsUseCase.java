package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import java.util.List;

/**
 * 주체가 가진 권한 코드를 모두 준다. 토큰에 실을 값이자 화면이 메뉴를 그릴 근거다.
 */
public interface ListSubjectPermissionsUseCase {

    List<String> of(Realm realm, String subjectId);
}
