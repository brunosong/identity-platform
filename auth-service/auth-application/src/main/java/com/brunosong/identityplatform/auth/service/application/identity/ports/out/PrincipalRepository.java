package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;

import java.util.Optional;

/**
 * Principal 저장 드리븐 포트.
 */
public interface PrincipalRepository {

    Optional<Principal> findById(PrincipalId principalId);

    Optional<Principal> findBySubjectId(SubjectId subjectId);

    Principal save(Principal principal);
}
