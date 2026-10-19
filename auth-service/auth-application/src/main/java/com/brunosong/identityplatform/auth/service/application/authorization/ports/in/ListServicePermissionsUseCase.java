package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;
import java.util.Map;

/**
 * 주체가 가진 권한을 서비스별로 묶어 준다(활성 역할, 활성 권한만). access 토큰의
 * {@code resource_access.<service>.roles} 에 실을 값이다.
 *
 * <p>서비스가 없는 권한(어드민 콘솔 자신의 것)은 빠진다. 토큰을 받는 서비스가 없다.
 */
public interface ListServicePermissionsUseCase {

    /** 키는 service_id, 값은 그 서비스의 권한 코드. */
    Map<String, List<String>> permissionsByService(Realm realm, String subjectId);
}
