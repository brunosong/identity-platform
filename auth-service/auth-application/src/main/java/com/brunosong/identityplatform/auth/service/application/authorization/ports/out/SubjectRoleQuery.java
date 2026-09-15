package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;

/**
 * 주체-역할 배정 조회 포트(SPI) — 읽기 모델을 바로 준다.
 *
 * <p>{@link #permissionCodes} 는 토큰 발급과 인가 판정이 요청마다 쓰는 경로다. 읽기를 따로 떼어
 * 둔 값어치가 가장 큰 자리이기도 하다 — 나중에 이 조회만 캐시나 조회 전용 모델로 옮길 수 있다.
 */
public interface SubjectRoleQuery {

    /** 주체의 모든 권한 코드(역할 → 권한 조인, 활성만). */
    List<String> permissionCodes(Realm realm, String subjectId);

    /** 주체에 배정된 역할. 활성 여부와 무관 — 배정 그대로. */
    List<RoleView> assignedRoles(Realm realm, String subjectId);

}
