package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;
import java.util.Optional;

/**
 * 권한 조회 포트(SPI) — 읽기 모델을 바로 준다.
 *
 * <p>조건으로 거르는 조회는 {@link PermissionSearchQuery} 가 맡는다. 여기 남는 것은 조건이 없어
 * 모양이 잘 바뀌지 않는 둘 — realm 전체와 단건이다.
 */
public interface PermissionQuery {

    /** 활성 권한 목록(분류·코드 순). */
    List<PermissionView> listActive(Realm realm);

    Optional<PermissionView> findById(Long permissionId);
}
