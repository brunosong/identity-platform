package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import java.util.List;
import java.util.Optional;

/**
 * 역할을 찾아본다. 관리 화면의 역할 조회가 이 하나를 쓴다.
 *
 * <p>조회는 명령과 달리 의도가 "보여줄 것을 달라" 하나라, 화면이 함께 쓰는 조회를 한 포트에 묶는다.
 * 명령은 바뀌는 것과 실패 조건이 저마다 달라 의도별로 가른다.
 */
public interface FindRolesUseCase {

    /** 활성 역할 목록. keyword 는 코드/이름 부분일치이며 비면 전체다. */
    List<RoleView> of(Realm realm, String keyword);

    Optional<RoleView> byId(Long roleId);

    /** 주체에 배정된 역할. */
    List<RoleView> ofSubject(Realm realm, String subjectId);
}
