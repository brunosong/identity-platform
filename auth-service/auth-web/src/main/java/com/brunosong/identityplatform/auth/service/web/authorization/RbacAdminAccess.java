package com.brunosong.identityplatform.auth.service.web.authorization;

import com.brunosong.identityplatform.auth.service.web.support.AuthenticatedCaller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인가 운영 API 의 접근 조건 — 호출자의 access 토큰을 검증하고 관리 권한을 확인한다.
 *
 * <p>인가 정책을 바꾸거나 들여다보는 API 라서, 앞단 게이트웨이가 있든 없든 이 서비스 자신이 막아야 한다.
 * 조건을 한 곳에 두어 컨트롤러마다 권한 코드 프로퍼티를 다시 읽지 않게 한다.
 */
@Component
public class RbacAdminAccess {

    private final AuthenticatedCaller caller;
    private final String managePermission;

    public RbacAdminAccess(AuthenticatedCaller caller,
                           @Value("${authorization.manage-permission:AUTHZ_MANAGE}") String managePermission) {
        this.caller = caller;
        this.managePermission = managePermission;
    }

    /** 관리 권한이 없으면 401/403 으로 끝낸다. */
    public void require(HttpServletRequest request) {
        caller.requirePermission(request, managePermission);
    }
}
