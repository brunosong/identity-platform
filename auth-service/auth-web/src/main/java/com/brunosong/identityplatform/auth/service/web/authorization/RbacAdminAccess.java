package com.brunosong.identityplatform.auth.service.web.authorization;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticatedCaller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인가 운영 API 의 접근 조건 — 호출자의 access 토큰을 검증하고 관리 권한을 확인한다.
 *
 * <p>인가 정책을 바꾸거나 들여다보는 API 라서, 앞단 게이트웨이가 있든 없든 이 서비스 자신이 막아야 한다.
 * 조건을 한 곳에 두어 컨트롤러마다 권한 코드 프로퍼티를 다시 읽지 않게 한다.
 *
 * <p><b>호출자 realm 은 언제나 어드민이다.</b> 이 API 들의 경로에는 realm 이 없다 — 경로/파라미터의
 * realm 은 <b>편집 대상</b>이지 호출자가 아니다(어드민이 포털의 정책을 고치는 것이 정상이다).
 * 그래서 검증 키를 어드민으로 못박는다. 포털 토큰은 서명 단계에서 거부되므로, 포털 쪽 권한 데이터에
 * {@code AUTHZ_MANAGE} 가 잘못 들어가더라도 이 API 는 열리지 않는다.
 * (Keycloak 의 {@code /admin/realms/{realm}/...} 도 호출자 realm 과 대상 realm 이 서로 다른 자리다.)
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

    /** 어드민 realm 토큰이 아니거나 관리 권한이 없으면 401/403 으로 끝낸다. */
    public void require(HttpServletRequest request) {
        caller.requirePermission(Realm.ADMIN, request, managePermission);
    }
}
