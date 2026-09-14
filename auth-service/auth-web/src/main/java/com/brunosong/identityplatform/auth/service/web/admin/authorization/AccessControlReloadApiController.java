package com.brunosong.identityplatform.auth.service.web.admin.authorization;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ReloadAccessRulesUseCase;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticatedCaller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * URL 접근 규칙 캐시를 DB 에서 다시 읽게 한다. 운영자가 명시적으로 실행하는 관리 작업이다.
 *
 * <p>RBAC 편집 API 와 같은 관리 권한을 요구한다. 인가 판정의 근거가 되는 캐시를 갈아끼우는 호출이라
 * 누구나 부를 수 있으면 안 된다.
 *
 * <p>realm 은 필수다 — 기본값을 두면 실수로 다른 realm 의 캐시를 건드린다.
 */
@RestController
@RequestMapping("/api/admin/rbac")
public class AccessControlReloadApiController {

    private final ReloadAccessRulesUseCase reloadAccessRules;
    private final AuthenticatedCaller caller;
    private final String managePermission;

    public AccessControlReloadApiController(
            ReloadAccessRulesUseCase reloadAccessRules,
            AuthenticatedCaller caller,
            @Value("${authorization.manage-permission:AUTHZ_MANAGE}") String managePermission) {
        this.reloadAccessRules = reloadAccessRules;
        this.caller = caller;
        this.managePermission = managePermission;
    }

    @PostMapping("/reload-url-rules")
    public ResponseEntity<Void> reloadUrlRules(HttpServletRequest request, @RequestParam Realm realm) {
        // 호출자는 어드민이고, 파라미터의 realm 은 캐시를 비울 대상이다 — 서로 다른 realm 이다.
        caller.requirePermission(Realm.ADMIN, request, managePermission);
        reloadAccessRules.reload(realm);
        return ResponseEntity.noContent().build();
    }
}
