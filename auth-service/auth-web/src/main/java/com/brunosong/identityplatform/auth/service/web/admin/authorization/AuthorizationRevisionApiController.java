package com.brunosong.identityplatform.auth.service.web.admin.authorization;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.BumpAuthorizationRevisionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.GetAuthorizationRevisionUseCase;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RBAC 리비전 운영 API.
 *
 * <p>리비전을 올리면 그 realm 의 기존 토큰이 모두 무효가 되어 전원 재로그인한다. 자동으로 불리지
 * 않는다 — 운영자가 명시적으로 실행한다.
 */
@RestController
@RequestMapping("/api/admin/rbac/revision")
@RequiredArgsConstructor
public class AuthorizationRevisionApiController {

    private final BumpAuthorizationRevisionUseCase bumpRevision;
    private final GetAuthorizationRevisionUseCase currentRevision;

    @GetMapping
    public RevisionResponse current(@RequestParam Realm realm) {
        return new RevisionResponse(realm, currentRevision.current(realm));
    }

    @PostMapping("/bump")
    public RevisionResponse bump(@RequestParam Realm realm) {
        bumpRevision.bump(realm);
        return new RevisionResponse(realm, currentRevision.current(realm));
    }

    public record RevisionResponse(Realm realm, long revision) {
    }
}
