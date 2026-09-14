package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 토큰에 실릴 모양 그대로 읽는다 — <b>역할</b>과 <b>서비스별 권한</b>.
 *
 * <p>역할(ADMIN, CUSTOMER)은 "이 사람이 조직에서 맡은 일" 이라 서비스로 갈리지 않는다.
 * 갈리는 것은 권한이다 — 역할 → 권한 매핑을 타고 나온 결과를 서비스별로 나눈다.
 *
 * <p>전에는 권한 코드를 평면 목록으로 실었다({@code authLs}). 그러면 어휘가 전역이라 서비스가
 * 늘수록 부딪히고, 남의 서비스 권한이 이 서비스의 문을 열 수 있었다. 이제 서비스로
 * 네임스페이스를 가르고, 그 이름이 무엇을 여는지는 각 서비스가 자기 코드로 정한다.
 *
 * <p>요청한 서비스({@code clientIds})의 역할만 읽는다 — 토큰의 {@code aud} 에 있는 서비스들이다.
 * 관계없는 서비스의 역할을 실으면 토큰이 realm 전체 크기로 자란다.
 */
public interface ListSubjectRolesUseCase {

    SubjectRoles of(Realm realm, String subjectId, Collection<String> clientIds);

    /**
     * @param realmRoles       역할 코드 — 토큰의 {@code realm_access.roles}
     * @param clientPermissions 서비스별 권한 코드 — 토큰의 {@code resource_access.{client}.roles}
     */
    record SubjectRoles(List<String> realmRoles, Map<String, List<String>> clientPermissions) {
    }
}
