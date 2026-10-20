package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListServicePermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.SubjectRoleQuery;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 주체에게 붙은 역할과 권한을 읽는 유스케이스 구현. 토큰 발급과 관리 API 체인이 부른다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectAuthorityService implements ListSubjectRolesUseCase, ListServicePermissionsUseCase,
        ListSubjectPermissionsUseCase {

    private final SubjectRoleQuery subjectRoleQuery;

    @Override
    public List<String> roleCodes(Realm realm, String subjectId) {
        return subjectRoleQuery.roleCodes(realm, subjectId);
    }

    @Override
    public Map<String, List<String>> permissionsByService(Realm realm, String subjectId) {
        return subjectRoleQuery.permissionsByService(realm, subjectId);
    }

    @Override
    public List<String> permissionCodes(Realm realm, String subjectId) {
        return subjectRoleQuery.permissionCodes(realm, subjectId);
    }
}
