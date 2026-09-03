package com.brunosong.identityplatform.auth.service.domain.identity.valueobject;

/**
 * 주체 유형 — 인증된 Principal 이 어느 백엔드의 누구를 가리키는지.
 *
 * <p>기존 Realm(ADMIN/PORTAL)과 대응한다: EMPLOYEE≈admin, CUSTOMER≈portal.
 * subjectId 의 의미가 유형별로 다르다(EMPLOYEE=esntlId, CUSTOMER=customerUuid).
 */
public enum SubjectType {
    EMPLOYEE,
    CUSTOMER
}
