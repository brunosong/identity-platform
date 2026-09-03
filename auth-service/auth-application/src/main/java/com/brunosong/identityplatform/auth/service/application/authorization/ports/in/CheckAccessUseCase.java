package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import java.util.Set;

/**
 * 요청 URL 과 메서드에 대해 이 권한들로 접근할 수 있는지 판정한다.
 *
 * <p>매칭되는 규칙이 없으면 realm 기본 정책으로 결정한다
 * (EMPLOYEE=보호 경로 거부, CUSTOMER=허용).
 */
public interface CheckAccessUseCase {

    boolean hasAccess(Realm realm, String requestUrl, String httpMethod, Set<String> userPermissions);
}
