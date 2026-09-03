package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;
import java.util.Optional;

/**
 * 역할 조회 포트(SPI) — 화면에 보여줄 모양(읽기 모델)을 바로 준다.
 *
 * <p>도메인 애그리거트를 복원하지 않는다. 조회는 불변식을 지킬 일이 없고, 복원을 거치지 않아야
 * 나중에 이 포트만 리플리카·조회 전용 테이블로 갈아끼울 수 있다.
 *
 * <p>조건으로 거르는 조회는 {@link RoleSearchQuery} 가 맡는다. 여기 남는 것은 조건이 없어
 * 모양이 잘 바뀌지 않는 둘 — realm 전체와 단건이다.
 */
public interface RoleQuery {

    /** 활성 역할 목록(role_id 순). */
    List<RoleView> listActive(Realm realm);

    Optional<RoleView> findById(Long roleId);
}
