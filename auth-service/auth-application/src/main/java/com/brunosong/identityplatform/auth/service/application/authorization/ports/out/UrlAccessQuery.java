package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlResourceView;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlRule;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;
import java.util.Optional;

/**
 * URL 접근 규칙 조회 포트(SPI) — 읽기 모델을 바로 준다.
 *
 * <p>표시용 분류(권한에서 유도, /page 는 별도)도 이 포트가 채워서 준다. 유도 규칙을 응용 계층에
 * 두면 목록을 읽을 때마다 권한 전체를 한 번 더 읽어야 한다.
 *
 * <p>조건으로 거르는 조회는 {@link UrlAccessSearchQuery} 가 맡는다. 여기 남는 것은 키로만 찾는
 * 것들이다 — realm 전체, 단건, 권한 기준 역방향, 그리고 판정 엔진용 규칙.
 *
 * <p>{@link #activeRules} 만 읽기 모델이 아니라 도메인 값 객체를 준다. 그것은 화면이 아니라
 * 인가 판정 엔진이 쓰는 캐시라, 판정 규칙(메서드 매칭·권한 OR)을 담은 도메인 타입이어야 한다.
 */
public interface UrlAccessQuery {

    /** 활성 URL 규칙 목록(정렬순서·패턴 순). */
    List<UrlAccessView> listActive(Realm realm);

    Optional<UrlAccessView> findById(Long urlAccessId);

    /** 특정 권한에 매핑된 활성 URL 리소스(권한 기준 역방향). */
    List<UrlResourceView> listByPermissionId(Long permissionId);

    /** 접근제어 엔진용 — 활성 URL 규칙을 (패턴, 메서드)별 권한코드 집합으로 묶어 준다. */
    List<UrlRule> activeRules(Realm realm);
}
