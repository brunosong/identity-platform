package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;

/**
 * 주체-역할 배정 쓰기 포트(SPI). 조회는 {@link SubjectRoleQuery} 가 맡는다.
 *
 * <p>주체 식별자(subjectId)는 auth-agnostic 문자열이다(EMPLOYEE=esntlId, CUSTOMER=customerUuid).
 */
public interface SubjectRoleRepository {

    /** 주체의 역할 배정을 입력 목록으로 교체. */
    void replaceRoles(Realm realm, String subjectId, List<Long> roleIds);

    /** 주체에 역할 하나를 가산 부여(이미 있으면 무시). 기존 역할은 유지한다. */
    void addRole(Realm realm, String subjectId, Long roleId);
}
