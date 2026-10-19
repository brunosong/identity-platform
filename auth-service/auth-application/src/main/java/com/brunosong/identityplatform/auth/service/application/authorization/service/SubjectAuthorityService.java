package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.SubjectRoleQuery;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 주체에게 붙은 역할과 권한을 읽는 유스케이스 구현. 토큰 발급이 부른다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectAuthorityService implements ListSubjectRolesUseCase {

    private final SubjectRoleQuery subjectRoleQuery;

    @Override
    public List<String> roleCodes(Realm realm, String subjectId) {
        return subjectRoleQuery.roleCodes(realm, subjectId);
    }
}
