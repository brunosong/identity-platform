package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.AuthorizationRevisionRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzRevisionJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** RBAC 리비전 쓰기 어댑터. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationRevisionRepositoryAdapter implements AuthorizationRevisionRepository {

    private final AuthzRevisionJpaRepository revisionRepository;

    @Override
    @Transactional
    public void bump(Realm realm) {
        int affected = revisionRepository.bump(realm);
        if (affected == 0) {
            log.warn("authz_revision row 가 없습니다(realm={}). seed 가 누락된 상태일 수 있습니다.", realm);
        }
    }
}
