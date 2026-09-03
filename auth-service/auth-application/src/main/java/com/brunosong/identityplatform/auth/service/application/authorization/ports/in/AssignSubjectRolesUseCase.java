package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import java.util.List;

/**
 * 주체의 역할 배정을 입력 목록으로 통째로 바꾼다.
 */
public interface AssignSubjectRolesUseCase {

    void assign(Realm realm, String subjectId, List<Long> roleIds);
}
