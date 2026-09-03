package com.brunosong.identityplatform.auth.service.dataaccess.authorization.adapter;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.SubjectRoleRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.entity.AuthzSubjectRoleEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.authorization.repository.AuthzSubjectRoleJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 주체-역할 배정 쓰기 어댑터. */
@Component
@RequiredArgsConstructor
public class SubjectRoleRepositoryAdapter implements SubjectRoleRepository {

    private final AuthzSubjectRoleJpaRepository subjectRoleRepository;

    @Override
    @Transactional
    public void replaceRoles(Realm realm, String subjectId, List<Long> roleIds) {
        subjectRoleRepository.deleteByRealmAndSubjectId(realm, subjectId);
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            subjectRoleRepository.save(assignment(realm, subjectId, roleId));
        }
    }

    @Override
    @Transactional
    public void addRole(Realm realm, String subjectId, Long roleId) {
        if (subjectRoleRepository.existsByRealmAndSubjectIdAndRoleId(realm, subjectId, roleId)) {
            return;
        }
        subjectRoleRepository.save(assignment(realm, subjectId, roleId));
    }

    private static AuthzSubjectRoleEntity assignment(Realm realm, String subjectId, Long roleId) {
        AuthzSubjectRoleEntity entity = new AuthzSubjectRoleEntity();
        entity.setRealm(realm);
        entity.setSubjectId(subjectId);
        entity.setRoleId(roleId);
        return entity;
    }
}
