package com.brunosong.identityplatform.auth.service.application.identity.session;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.SingleSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SessionRegistryPort;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;

/**
 * {@link SingleSessionUseCase} 구현 — 게이트웨이의 세션 검증/무효화를 {@link SessionRegistryPort} 로 위임한다.
 */
@RequiredArgsConstructor
public class SingleSessionService implements SingleSessionUseCase {

    private final SessionRegistryPort sessionRegistry;

    @Override
    public boolean isCurrent(Realm realm, String subjectId, String sid) {
        return sessionRegistry.isCurrent(realm, subjectId, sid);
    }

    @Override
    public void invalidate(Realm realm, String subjectId) {
        sessionRegistry.close(realm, subjectId);
    }
}
