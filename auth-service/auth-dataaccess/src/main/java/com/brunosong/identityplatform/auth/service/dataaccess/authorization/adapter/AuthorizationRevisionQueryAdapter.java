package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.AuthorizationRevisionQuery;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzRevisionJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** RBAC 리비전 조회 어댑터. seed 가 없으면 1 로 본다(아직 아무 정책도 바뀌지 않은 상태). */
@Component
@RequiredArgsConstructor
public class AuthorizationRevisionQueryAdapter implements AuthorizationRevisionQuery {

    private final AuthzRevisionJpaRepository revisionRepository;

    @Override
    @Transactional(readOnly = true)
    public long current(Realm realm) {
        Long revision = revisionRepository.currentRevision(realm);
        return revision == null ? 1L : revision;
    }
}
